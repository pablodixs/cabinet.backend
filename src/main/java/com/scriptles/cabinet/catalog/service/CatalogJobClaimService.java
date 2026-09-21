package com.scriptles.cabinet.catalog.service;

import com.scriptles.cabinet.catalog.entity.CatalogJob;
import com.scriptles.cabinet.catalog.entity.CatalogJobAttempt;
import com.scriptles.cabinet.catalog.entity.CatalogOperation;
import com.scriptles.cabinet.catalog.entity.CatalogOperationEvent;
import com.scriptles.cabinet.catalog.repository.CatalogJobAttemptRepository;
import com.scriptles.cabinet.catalog.repository.CatalogJobRepository;
import com.scriptles.cabinet.catalog.repository.CatalogOperationEventRepository;
import com.scriptles.cabinet.catalog.repository.CatalogOperationRepository;
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
public class CatalogJobClaimService {
    private final CatalogJobRepository jobs;
    private final CatalogJobAttemptRepository attempts;
    private final CatalogOperationRepository operations;
    private final CatalogOperationEventRepository events;

    @Transactional
    public List<ClaimedJob> claim(String workerId, int limit) {
        if (limit <= 0) return List.of();
        Instant now = Instant.now();
        List<CatalogJob> claimable = jobs.claimable(now, PageRequest.of(0, limit));
        for (CatalogJob job : claimable) {
            job.setStatus(PROCESSING);
            job.setAttempts(job.getAttempts() + 1);
            job.setLockedAt(now);
            job.setLockedBy(workerId);
            job.setStartedAt(now);
            job.setUpdatedAt(now);
            CatalogJobAttempt attempt = new CatalogJobAttempt();
            attempt.setId(UUID.randomUUID());
            attempt.setJobId(job.getId());
            attempt.setAttemptNumber(job.getAttempts());
            attempt.setWorkerId(workerId);
            attempt.setStatus("RUNNING");
            attempt.setStartedAt(now);
            attempts.save(attempt);
            if (job.getOperationId() != null) {
                operations.findById(job.getOperationId()).ifPresent(operation -> {
                    if (!RUNNING.equals(operation.getStatus())) {
                        operation.setStatus(RUNNING);
                        operation.setStartedAt(operation.getStartedAt() == null ? now : operation.getStartedAt());
                        operation.setUpdatedAt(now);
                    }
                });
            }
        }
        jobs.saveAll(claimable);
        return claimable.stream().map(job -> new ClaimedJob(job.getId(), job.getAttempts())).toList();
    }

    @Transactional
    public int recoverStale(Instant cutoff) {
        Instant now = Instant.now();
        List<CatalogJob> stale = jobs.staleProcessing(cutoff, PageRequest.of(0, 100));
        int recovered = 0;
        for (CatalogJob job : stale) {
            job.setStatus(RETRY);
            job.setAvailableAt(now);
            job.setLockedAt(null);
            job.setLockedBy(null);
            job.setLastError("Processing lock expired; job returned to the queue");
            job.setUpdatedAt(now);
            attempts.findByJobIdAndAttemptNumber(job.getId(), job.getAttempts()).ifPresent(attempt -> {
                attempt.setStatus("ABANDONED");
                attempt.setFinishedAt(now);
                attempt.setDurationMs(Math.max(0, now.toEpochMilli() - attempt.getStartedAt().toEpochMilli()));
            });
            if (job.getOperationId() != null) {
                CatalogOperationEvent event = new CatalogOperationEvent();
                event.setId(UUID.randomUUID());
                event.setOperationId(job.getOperationId());
                event.setJobId(job.getId());
                event.setEventType("STALE_LOCK_RECOVERED");
                event.setSeverity("WARNING");
                event.setMessage("A stale worker lock expired; the job was returned to the queue");
                event.setOccurredAt(now);
                events.save(event);
            }
            jobs.save(job);
            recovered++;
        }
        return recovered;
    }

    public record ClaimedJob(UUID jobId, int attemptNumber) {
    }
}
