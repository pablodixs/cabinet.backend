package com.scriptles.cabinet.catalog.service;

import com.scriptles.cabinet.catalog.api.CatalogOperationsResponses.*;
import com.scriptles.cabinet.catalog.entity.*;
import com.scriptles.cabinet.catalog.repository.*;
import com.scriptles.cabinet.common.api.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.scriptles.cabinet.catalog.service.CatalogJobTypes.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogOperationsModerationService {
    private final CatalogOperationRepository operations;
    private final CatalogJobRepository jobs;
    private final CatalogJobAttemptRepository attempts;
    private final CatalogOperationEventRepository events;
    private final ExternalCatalogEntityRepository externalEntities;
    private final CatalogJobLifecycleService lifecycle;

    public PageResponse<OperationSummary> listOperations(String status, String type, String provider,
            String trigger, String query, int page, int size) {
        String normalizedQuery = blankToNull(query);
        UUID rootId = parseUuid(normalizedQuery);
        var results = operations.search(blankToNull(status), blankToNull(type), blankToNull(provider),
                blankToNull(trigger), normalizedQuery, rootId, PageRequest.of(Math.max(0, page), clampSize(size)));
        return PageResponse.from(results.map(this::summary));
    }

    public OperationDetails operation(UUID id) {
        CatalogOperation operation = operations.findById(id)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND));
        List<JobSummary> jobSummaries = jobs.findByOperationIdOrderByCreatedAtAsc(id).stream().limit(500)
                .map(this::jobSummary).toList();
        List<Event> timeline = events.findByOperationIdOrderByOccurredAtAsc(id).stream().limit(1000).map(event ->
                new Event(event.getEventType(), event.getSeverity(), event.getEntityType(), event.getEntityId(),
                        event.getExternalId(), event.getMessage(), event.getMetadata(), event.getOccurredAt())).toList();
        return new OperationDetails(summary(operation), jobSummaries, timeline);
    }

    public PageResponse<JobSummary> listJobs(String status, String type, String provider,
            String query, int page, int size) {
        String normalizedQuery = blankToNull(query);
        var results = jobs.search(blankToNull(status), blankToNull(type), blankToNull(provider),
                normalizedQuery, parseUuid(normalizedQuery), PageRequest.of(Math.max(0, page), clampSize(size)));
        return PageResponse.from(results.map(this::jobSummary));
    }

    public JobDetails job(UUID id) {
        CatalogJob job = jobs.findById(id)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND));
        List<Attempt> history = attempts.findByJobIdOrderByAttemptNumberDesc(id).stream().map(attempt ->
                new Attempt(attempt.getAttemptNumber(), attempt.getWorkerId(), attempt.getStatus(),
                        attempt.getStartedAt(), attempt.getFinishedAt(), attempt.getDurationMs(),
                        attempt.getErrorClass(), attempt.getErrorMessage(), attempt.getMetrics())).toList();
        return new JobDetails(jobSummary(job), job.getDeduplicationKey(), job.getPayload(), history);
    }

    public QueueStatus queueStatus() {
        Instant now = Instant.now();
        return new QueueStatus(jobs.countByStatus(PENDING), jobs.countByStatus(PROCESSING),
                jobs.countByStatus(RETRY), jobs.countByStatus(DEAD),
                age(jobs.findOldestCreatedAtByStatus(PENDING), now),
                age(jobs.findOldestCreatedAtByStatus(RETRY), now));
    }

    public PageResponse<ExternalEntity> externalCatalog(String provider, String entityType, String state,
            String query, int page, int size) {
        var results = externalEntities.search(blankToNull(provider), blankToNull(entityType), blankToNull(state),
                blankToNull(query), PageRequest.of(Math.max(0, page), clampSize(size)));
        return PageResponse.from(results.map(entity -> new ExternalEntity(entity.getId(), entity.getProvider(),
                entity.getEntityType(), entity.getExternalId(), entity.getDisplayName(), entity.getState(),
                entity.getFirstSeenAt(), entity.getLastSeenAt(), entity.getRemovedAt(), entity.getSourceMetadata())));
    }

    @Transactional
    public UUID retry(UUID jobId) {
        return lifecycle.retryDeadJob(jobId);
    }

    private OperationSummary summary(CatalogOperation operation) {
        return new OperationSummary(operation.getId(), operation.getType(), operation.getProvider(),
                operation.getTrigger(), operation.getStatus(), operation.getRequestedBy(),
                operation.getRootEntityType(), operation.getRootExternalId(), operation.getRootCollectionId(),
                operation.getTotalItems(), operation.getProcessedItems(), operation.getCreatedItems(),
                operation.getUpdatedItems(), operation.getUnchangedItems(), operation.getFailedItems(),
                operation.getCreatedAt(), operation.getStartedAt(), operation.getCompletedAt());
    }

    private JobSummary jobSummary(CatalogJob job) {
        return new JobSummary(job.getId(), job.getOperationId(), job.getJobType(), job.getProvider(),
                job.getEntityType(), job.getExternalId(), job.getCollectionId(), job.getMediaId(), job.getPriority(),
                job.getTrigger(), job.getStatus(), job.getAttempts(), job.getAvailableAt(), job.getLockedAt(),
                job.getLockedBy(), job.getCreatedAt(), job.getStartedAt(), job.getCompletedAt(), job.getLastError());
    }

    private Long age(Instant createdAt, Instant now) {
        return createdAt == null ? null : Math.max(0, now.getEpochSecond() - createdAt.getEpochSecond());
    }

    private int clampSize(int size) {
        return Math.max(1, Math.min(100, size));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private UUID parseUuid(String value) {
        if (value == null) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
