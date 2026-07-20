package com.scriptles.cabinet.user.importer;

import lombok.RequiredArgsConstructor;
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
    private final LetterboxdImportMatcher matcher;
    private final LetterboxdImportApplier applier;
    private final LetterboxdImportService service;

    public LetterboxdImportScheduler(
            @Qualifier("letterboxdImportExecutor") Executor executor,
            LetterboxdImportJobRepository jobRepository,
            LetterboxdImportItemRepository itemRepository,
            LetterboxdImportMatcher matcher,
            LetterboxdImportApplier applier,
            LetterboxdImportService service
    ) {
        this.executor = executor;
        this.jobRepository = jobRepository;
        this.itemRepository = itemRepository;
        this.matcher = matcher;
        this.applier = applier;
        this.service = service;
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
            job.setState(LetterboxdImportJobState.READY);
            service.refreshCounts(job);
            jobRepository.save(job);
            double automaticRate = job.getTotalItems() == 0
                    ? 0
                    : (double) job.getMatchedItems() / job.getTotalItems();
            log.info("Letterboxd import matching metrics: job={}, durationMs={}, automaticMatchRate={}, unresolvedItems={}, externalFailures={}",
                    jobId, elapsedMillis(startedAt), automaticRate, job.getReviewItems(), externalFailures);
        } catch (RuntimeException exception) {
            failJob(jobId, exception);
        }
    }

    @Scheduled(cron = "0 17 * * * *")
    @Transactional
    public void expireAndRedact() {
        Instant now = Instant.now();
        jobRepository.findAllByExpiresAtBeforeAndStateIn(now, EnumSet.of(LetterboxdImportJobState.READY))
                .forEach(job -> {
                    job.setState(LetterboxdImportJobState.CANCELLED);
                    job.setCompletedAt(now);
                    itemRepository.redactPayloads(job.getId());
                    job.setExpiresAt(null);
                });
        jobRepository.findAllByExpiresAtBeforeAndStateIn(now, EnumSet.of(
                LetterboxdImportJobState.COMPLETED,
                LetterboxdImportJobState.COMPLETED_WITH_ERRORS,
                LetterboxdImportJobState.FAILED,
                LetterboxdImportJobState.CANCELLED
        )).forEach(job -> {
            itemRepository.redactPayloads(job.getId());
            job.setExpiresAt(null);
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
                    LetterboxdImportItem failed = itemRepository.findById(item.getId()).orElse(item);
                    failed.setState(LetterboxdImportItemState.FAILED);
                    failed.setErrorMessage(safeMessage(exception));
                    itemRepository.save(failed);
                    log.warn("Letterboxd apply failed for job {} item {}", jobId, item.getId(), exception);
                }
            }
            job = jobRepository.findById(jobId).orElseThrow();
            service.refreshCounts(job);
            job.setState(job.getFailedItems() == 0
                    ? LetterboxdImportJobState.COMPLETED
                    : LetterboxdImportJobState.COMPLETED_WITH_ERRORS);
            job.setCompletedAt(Instant.now());
            job.setExpiresAt(Instant.now().plus(java.time.Duration.ofDays(7)));
            jobRepository.save(job);
            log.info("Letterboxd import application metrics: job={}, durationMs={}, importedItems={}, preservedItems={}, failedItems={}",
                    jobId, elapsedMillis(startedAt), job.getImportedItems(), job.getPreservedItems(), job.getFailedItems());
        } catch (RuntimeException exception) {
            failJob(jobId, exception);
        }
    }

    private void failJob(UUID jobId, RuntimeException exception) {
        jobRepository.findById(jobId).ifPresent(job -> {
            job.setState(LetterboxdImportJobState.FAILED);
            job.setErrorMessage(safeMessage(exception));
            job.setCompletedAt(Instant.now());
            job.setExpiresAt(Instant.now().plus(java.time.Duration.ofDays(7)));
            jobRepository.save(job);
        });
        log.error("Letterboxd import job {} failed", jobId, exception);
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
