package com.scriptles.cabinet.user.importer;

import com.scriptles.cabinet.notifications.service.NotificationService;
import com.scriptles.cabinet.status.BackgroundJobRunner;
import com.scriptles.cabinet.status.BackgroundJobTracker;
import com.scriptles.cabinet.status.JobKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.Executor;

@Component
@Slf4j
public class LetterboxdImportScheduler {
    private final Executor executor;
    private final LetterboxdImportJobRepository jobRepository;
    private final LetterboxdImportItemRepository itemRepository;
    private final BackgroundJobRunner jobRunner;
    private final LetterboxdImportMatcher matcher;
    private final LetterboxdImportApplier applier;
    private final LetterboxdImportService service;
    private final NotificationService notificationService;

    public LetterboxdImportScheduler(
            @Qualifier("letterboxdImportExecutor") Executor executor,
            LetterboxdImportJobRepository jobRepository,
            LetterboxdImportItemRepository itemRepository,
            BackgroundJobRunner jobRunner,
            LetterboxdImportMatcher matcher,
            LetterboxdImportApplier applier,
            LetterboxdImportService service,
            NotificationService notificationService
    ) {
        this.executor = executor;
        this.jobRepository = jobRepository;
        this.itemRepository = itemRepository;
        this.jobRunner = jobRunner;
        this.matcher = matcher;
        this.applier = applier;
        this.service = service;
        this.notificationService = notificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void requested(LetterboxdImportRequestedEvent event) {
        schedule(event.jobId(), event.action());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recover() {
        jobRepository.findAllByStateIn(EnumSet.of(
                LetterboxdImportJobState.MATCHING,
                LetterboxdImportJobState.IMPORTING
        )).forEach(job -> schedule(job.getId(), job.getState() == LetterboxdImportJobState.MATCHING
                ? LetterboxdImportRequestedEvent.Action.MATCH
                : LetterboxdImportRequestedEvent.Action.APPLY));
    }

    private void schedule(UUID jobId, LetterboxdImportRequestedEvent.Action action) {
        executor.execute(() -> {
            if (action == LetterboxdImportRequestedEvent.Action.MATCH) match(jobId);
            else apply(jobId);
        });
    }

    private void match(UUID jobId) {
        long startedAt = System.nanoTime();
        int externalFailures = 0;
        LetterboxdImportJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null || job.getState() != LetterboxdImportJobState.MATCHING) return;
        try {
            for (LetterboxdImportItem item : itemRepository.findAllByJobIdOrderByCreatedAtAsc(jobId)) {
                if (item.getState() != LetterboxdImportItemState.PENDING) continue;
                try {
                    matcher.match(item);
                } catch (RuntimeException exception) {
                    externalFailures++;
                    item.setState(LetterboxdImportItemState.NEEDS_REVIEW);
                    item.setErrorMessage("Não foi possível consultar o TMDB; escolha o filme manualmente");
                    itemRepository.save(item);
                    log.warn("Letterboxd match failed for job {} item {}", jobId, item.getId(), exception);
                }
            }
            job = jobRepository.findById(jobId).orElseThrow();
            if (job.getState() == LetterboxdImportJobState.CANCELLED) return;
            if (jobRepository.transitionState(jobId, LetterboxdImportJobState.MATCHING,
                    LetterboxdImportJobState.READY, null, null, null) > 0) {
                job = jobRepository.findById(jobId).orElseThrow();
                service.refreshCounts(job);
                notifyReady(job);
            }
            double automaticRate = job.getTotalItems() == 0
                    ? 0
                    : (double) job.getMatchedItems() / job.getTotalItems();
            log.info("Letterboxd import matching metrics: job={}, durationMs={}, automaticMatchRate={}, unresolvedItems={}, externalFailures={}",
                    jobId, elapsedMillis(startedAt), automaticRate, job.getReviewItems(), externalFailures);
        } catch (RuntimeException exception) {
            failJob(jobId, LetterboxdImportJobState.MATCHING, exception);
        }
    }

    @Scheduled(cron = "${app.letterboxd.cleanup-cron}")
    @Transactional
    public void expireAndRedact() {
        jobRunner.execute(JobKey.LETTERBOXD_CLEANUP, () -> {
            Instant now = Instant.now();
            var expired = jobRepository.findAllByExpiresAtBeforeAndStateIn(
                    now, EnumSet.of(LetterboxdImportJobState.READY));
            int redactedItems = 0;
            for (LetterboxdImportJob job : expired) {
                job.setState(LetterboxdImportJobState.CANCELLED);
                job.setCompletedAt(now);
                redactedItems += itemRepository.redactPayloads(job.getId());
                job.setExpiresAt(null);
            }
            var terminal = jobRepository.findAllByExpiresAtBeforeAndStateIn(now, EnumSet.of(
                    LetterboxdImportJobState.COMPLETED,
                    LetterboxdImportJobState.COMPLETED_WITH_ERRORS,
                    LetterboxdImportJobState.FAILED,
                    LetterboxdImportJobState.CANCELLED));
            for (LetterboxdImportJob job : terminal) {
                redactedItems += itemRepository.redactPayloads(job.getId());
                job.setExpiresAt(null);
            }
            int jobs = expired.size() + terminal.size();
            return BackgroundJobTracker.JobRunResult.completed(jobs, redactedItems, jobs, 0,
                    "Expired import details were removed");
        });
    }

    private void apply(UUID jobId) {
        long startedAt = System.nanoTime();
        LetterboxdImportJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null || job.getState() != LetterboxdImportJobState.IMPORTING) return;
        try {
            for (LetterboxdImportItem item : itemRepository.findAllByJobIdOrderByCreatedAtAsc(jobId)) {
                if (item.getState() != LetterboxdImportItemState.AUTO_MATCHED
                        && item.getState() != LetterboxdImportItemState.RESOLVED
                        && item.getState() != LetterboxdImportItemState.FAILED) continue;
                try {
                    applier.apply(item.getId());
                } catch (RuntimeException exception) {
                    itemRepository.markApplyFailedIfRetryable(item.getId(), safeMessage(exception));
                    log.warn("Letterboxd apply failed for job {} item {}", jobId, item.getId(), exception);
                }
            }
            job = jobRepository.findById(jobId).orElseThrow();
            LetterboxdImportJobState targetState = itemRepository.countByJobIdAndState(
                    jobId, LetterboxdImportItemState.FAILED) == 0
                    ? LetterboxdImportJobState.COMPLETED
                    : LetterboxdImportJobState.COMPLETED_WITH_ERRORS;
            Instant completedAt = Instant.now();
            if (jobRepository.transitionState(jobId, LetterboxdImportJobState.IMPORTING, targetState,
                    completedAt, completedAt.plus(java.time.Duration.ofDays(7)), null) > 0) {
                job = jobRepository.findById(jobId).orElseThrow();
                service.refreshCounts(job);
                job.setState(targetState);
                job.setCompletedAt(completedAt);
                job.setExpiresAt(completedAt.plus(java.time.Duration.ofDays(7)));
                notifyCompleted(job);
            }
            log.info("Letterboxd import application metrics: job={}, durationMs={}, importedItems={}, preservedItems={}, failedItems={}",
                    jobId, elapsedMillis(startedAt), job.getImportedItems(), job.getPreservedItems(), job.getFailedItems());
        } catch (RuntimeException exception) {
            failJob(jobId, LetterboxdImportJobState.IMPORTING, exception);
        }
    }

    private void failJob(UUID jobId, LetterboxdImportJobState expectedState, RuntimeException exception) {
        Instant now = Instant.now();
        jobRepository.transitionState(jobId, expectedState, LetterboxdImportJobState.FAILED,
                now, now.plus(java.time.Duration.ofDays(7)), safeMessage(exception));
        log.error("Letterboxd import job {} failed", jobId, exception);
    }

    private void notifyReady(LetterboxdImportJob job) {
        try {
            notificationService.letterboxdImportReady(job);
        } catch (RuntimeException exception) {
            log.error("Could not notify that Letterboxd import job {} is ready", job.getId(), exception);
        }
    }

    private void notifyCompleted(LetterboxdImportJob job) {
        try {
            notificationService.letterboxdImportCompleted(job);
        } catch (RuntimeException exception) {
            log.error("Could not notify that Letterboxd import job {} is completed", job.getId(), exception);
        }
    }

    private String safeMessage(RuntimeException exception) {
        String value = exception.getMessage();
        if (value == null || value.isBlank()) return "Falha inesperada durante a importação";
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private long elapsedMillis(long startedAt) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }
}
