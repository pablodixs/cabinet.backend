package com.scriptles.cabinet.catalog.service;

import com.scriptles.cabinet.catalog.entity.CatalogJob;
import com.scriptles.cabinet.catalog.entity.CatalogJobAttempt;
import com.scriptles.cabinet.catalog.entity.CatalogOperation;
import com.scriptles.cabinet.catalog.entity.CatalogOperationEvent;
import com.scriptles.cabinet.catalog.entity.CollectionSourceItem;
import com.scriptles.cabinet.catalog.repository.CatalogJobAttemptRepository;
import com.scriptles.cabinet.catalog.repository.CatalogJobRepository;
import com.scriptles.cabinet.catalog.repository.CatalogOperationEventRepository;
import com.scriptles.cabinet.catalog.repository.CatalogOperationRepository;
import com.scriptles.cabinet.catalog.repository.CollectionSourceItemRepository;
import com.scriptles.cabinet.media.external.ExternalMediaNotFoundException;
import com.scriptles.cabinet.media.external.ExternalMediaRateLimitException;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

import static com.scriptles.cabinet.catalog.service.CatalogJobTypes.*;

@Service
@RequiredArgsConstructor
public class CatalogJobLifecycleService {
    private static final List<String> ACTIVE = List.of(PENDING, PROCESSING, RETRY);
    private static final Pattern SENSITIVE = Pattern.compile("(?i)(api[_-]?key|access[_-]?token|authorization)(\\s*[=:]\\s*|\\s+)[^\\s,;]+|Bearer\\s+[^\\s,;]+");

    private final CatalogJobRepository jobs;
    private final CatalogJobAttemptRepository attempts;
    private final CatalogOperationRepository operations;
    private final CatalogOperationEventRepository events;
    private final CollectionSourceItemRepository sourceItems;
    private final MeterRegistry meters;

    @Value("${catalog.jobs.max-attempts:5}")
    private int maxAttempts;
    @Value("${catalog.jobs.retry-base:30s}")
    private Duration retryBase;
    @Value("${catalog.jobs.retry-max:12h}")
    private Duration retryMax;

    @Transactional
    public boolean complete(UUID jobId, int attemptNumber, Map<String, Object> result) {
        CatalogJob job = currentClaim(jobId, attemptNumber);
        if (job == null) return false;
        Instant now = Instant.now();
        job.setStatus(COMPLETED);
        job.setCompletedAt(now);
        job.setLockedAt(null);
        job.setLockedBy(null);
        job.setLastError(null);
        job.setUpdatedAt(now);
        CatalogJobAttempt attempt = attempts.findByJobIdAndAttemptNumber(jobId, attemptNumber).orElseThrow();
        finishAttempt(attempt, now, "COMPLETED", null, result);
        jobs.saveAndFlush(job);
        meters.counter("cabinet.catalog.jobs.completed", "type", job.getJobType()).increment();
        updateOperation(job, result, false, now);
        return true;
    }

    @Transactional
    public boolean fail(UUID jobId, int attemptNumber, RuntimeException failure) {
        CatalogJob job = currentClaim(jobId, attemptNumber);
        if (job == null) return false;
        Instant now = Instant.now();
        String message = safeMessage(failure);
        boolean permanent = failure instanceof ExternalMediaNotFoundException
                || failure instanceof IllegalArgumentException;
        boolean dead = permanent || attemptNumber >= maxAttempts;
        Duration rateLimitDelay = retryMax.compareTo(Duration.ofHours(1)) < 0 ? retryMax : Duration.ofHours(1);
        Duration delay = failure instanceof ExternalMediaRateLimitException
                ? rateLimitDelay(failure, rateLimitDelay)
                : backoff(attemptNumber);
        String status = dead ? DEAD : RETRY;
        job.setStatus(status);
        job.setLastError(message);
        job.setAvailableAt(dead ? now : now.plus(delay));
        job.setCompletedAt(dead ? now : null);
        job.setLockedAt(null);
        job.setLockedBy(null);
        job.setUpdatedAt(now);
        CatalogJobAttempt attempt = attempts.findByJobIdAndAttemptNumber(jobId, attemptNumber).orElseThrow();
        finishAttempt(attempt, now, dead ? DEAD : RETRY, failure, Map.of());
        if (CatalogJobTypes.COLLECTION_ITEM_MATERIALIZE.equals(job.getJobType())) {
            sourceItems.findByProviderAndExternalIdAndSourcePresentTrue(
                    job.getProvider(), job.getExternalId()).forEach(item -> {
                item.setResolutionStatus(dead ? "FAILED" : "QUEUED");
                item.setLastMaterializationError(message);
                item.setUpdatedAt(now);
            });
        }
        jobs.saveAndFlush(job);
        if (dead) {
            meters.counter("cabinet.catalog.jobs.failed", "type", job.getJobType()).increment();
            addEvent(job, "JOB_DEAD", "ERROR", "Job moved to dead letter: " + message, now);
        } else {
            meters.counter("cabinet.catalog.jobs.retried", "type", job.getJobType()).increment();
            addEvent(job, failure instanceof ExternalMediaRateLimitException ? "RATE_LIMITED" : "JOB_RETRY_SCHEDULED",
                    "WARNING", "Retry scheduled after " + delay.toSeconds() + " seconds", now);
        }
        updateOperation(job, Map.of(), dead, now);
        return true;
    }

    @Transactional
    public void submissionFailed(UUID jobId, int attemptNumber, RuntimeException failure) {
        fail(jobId, attemptNumber, failure);
    }

    @Transactional
    public UUID retryDeadJob(UUID jobId) {
        CatalogJob job = jobs.findById(jobId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!DEAD.equals(job.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only dead jobs can be retried manually");
        }
        boolean collision = jobs.existsByDeduplicationKeyAndStatusIn(job.getDeduplicationKey(), ACTIVE);
        if (collision) throw new ResponseStatusException(HttpStatus.CONFLICT, "Equivalent work is already active");
        Instant now = Instant.now();
        job.setStatus(PENDING);
        job.setAvailableAt(now);
        job.setCompletedAt(null);
        job.setLastError(null);
        job.setLockedAt(null);
        job.setLockedBy(null);
        job.setUpdatedAt(now);
        if (job.getOperationId() != null) {
            operations.findById(job.getOperationId()).ifPresent(operation -> {
                operation.setStatus(QUEUED);
                operation.setCompletedAt(null);
                operation.setUpdatedAt(now);
            });
            addEvent(job, "JOB_MANUALLY_REQUEUED", "WARNING", "Moderator queued a new attempt", now);
        }
        return job.getId();
    }

    private CatalogJob currentClaim(UUID jobId, int attemptNumber) {
        CatalogJob job = jobs.findById(jobId).orElse(null);
        if (job == null || !PROCESSING.equals(job.getStatus()) || job.getAttempts() != attemptNumber) return null;
        return job;
    }

    private void updateOperation(CatalogJob job, Map<String, Object> result, boolean failed, Instant now) {
        if (job.getOperationId() == null) return;
        CatalogOperation operation = operations.findById(job.getOperationId()).orElse(null);
        if (operation == null) return;
        if (result.containsKey("totalItems")) operation.setTotalItems(number(result.get("totalItems")));
        operation.setProcessedItems(operation.getProcessedItems() + number(result.get("processedItems")) + (failed ? 1 : 0));
        operation.setCreatedItems(operation.getCreatedItems() + number(result.get("createdItems")));
        operation.setUpdatedItems(operation.getUpdatedItems() + number(result.get("updatedItems")));
        operation.setUnchangedItems(operation.getUnchangedItems() + number(result.get("unchangedItems")));
        operation.setFailedItems(operation.getFailedItems() + number(result.get("failedItems")) + (failed ? 1 : 0));
        long waiting = jobs.countByOperationIdAndStatusIn(job.getOperationId(), ACTIVE);
        if (waiting == 0) {
            boolean rootFailure = failed && (TMDB_COLLECTION_HYDRATE.equals(job.getJobType())
                    || COLLECTION_SYNC.equals(job.getJobType())
                    || EXTERNAL_CATALOG_INDEX.equals(job.getJobType()));
            operation.setStatus(rootFailure ? FAILED
                    : operation.getFailedItems() > 0 ? COMPLETED_WITH_WARNINGS : COMPLETED);
            if (rootFailure) operation.setLastError(job.getLastError());
            operation.setCompletedAt(now);
            addEvent(job, "OPERATION_COMPLETED", operation.getFailedItems() > 0 ? "WARNING" : "INFO",
                    operation.getFailedItems() > 0 ? "Operation completed with warnings" : "Operation completed", now);
            meters.counter("cabinet.catalog.operations.completed", "status", operation.getStatus()).increment();
        }
        operation.setUpdatedAt(now);
    }

    private void finishAttempt(CatalogJobAttempt attempt, Instant now, String status, RuntimeException failure,
                               Map<String, Object> metrics) {
        attempt.setStatus(status);
        attempt.setFinishedAt(now);
        attempt.setDurationMs(Math.max(0, now.toEpochMilli() - attempt.getStartedAt().toEpochMilli()));
        if (failure != null) {
            attempt.setErrorClass(failure.getClass().getName());
            attempt.setErrorMessage(safeMessage(failure));
        }
        attempt.setMetrics(metrics);
    }

    private void addEvent(CatalogJob job, String type, String severity, String message, Instant now) {
        if (job.getOperationId() == null) return;
        CatalogOperationEvent event = new CatalogOperationEvent();
        event.setId(UUID.randomUUID());
        event.setOperationId(job.getOperationId());
        event.setJobId(job.getId());
        event.setEventType(type);
        event.setSeverity(severity);
        event.setEntityType(job.getEntityType());
        event.setExternalId(job.getExternalId());
        event.setMessage(message.length() > 1000 ? message.substring(0, 1000) : message);
        event.setOccurredAt(now);
        events.save(event);
    }

    private Duration backoff(int attempt) {
        long multiplier = 1L << Math.min(20, Math.max(0, attempt - 1));
        long base = Math.min(retryMax.toMillis(), retryBase.toMillis() * multiplier);
        long jitter = ThreadLocalRandom.current().nextLong(Math.max(1, base / 5 + 1));
        return Duration.ofMillis(Math.min(retryMax.toMillis(), base + jitter));
    }

    private Duration rateLimitDelay(RuntimeException failure, Duration fallback) {
        if (failure instanceof ExternalMediaRateLimitException rateLimit
                && rateLimit.getRetryAfter() != null && !rateLimit.getRetryAfter().isNegative()) {
            return rateLimit.getRetryAfter().compareTo(retryMax) > 0 ? retryMax : rateLimit.getRetryAfter();
        }
        return fallback;
    }

    private String safeMessage(Throwable failure) {
        Throwable mostSpecific = failure;
        for (Throwable cause = failure.getCause(); cause != null && cause != mostSpecific; cause = cause.getCause()) {
            mostSpecific = cause;
        }
        String message = mostSpecific.getMessage();
        if (message == null || message.isBlank()) message = failure.getMessage();
        if (message == null || message.isBlank()) message = "Job failed";
        if (mostSpecific != failure && mostSpecific.getMessage() != null && !mostSpecific.getMessage().isBlank()) {
            message = mostSpecific.getClass().getSimpleName() + ": " + message;
        }
        message = SENSITIVE.matcher(message).replaceAll("$1=[redacted]");
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    private long number(Object value) {
        return value instanceof Number numeric ? numeric.longValue() : 0;
    }
}
