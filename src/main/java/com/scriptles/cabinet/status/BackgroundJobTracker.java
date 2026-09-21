package com.scriptles.cabinet.status;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BackgroundJobTracker {
    private final BackgroundJobRunRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID start(JobKey jobKey) {
        BackgroundJobRun run = new BackgroundJobRun();
        run.setJobKey(jobKey);
        run.setStatus(JobRunStatus.RUNNING);
        run.setStartedAt(Instant.now());
        return repository.saveAndFlush(run).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BackgroundJobRun complete(UUID runId, JobRunResult result) {
        BackgroundJobRun run = repository.findById(runId).orElseThrow();
        run.setFinishedAt(Instant.now());
        run.setProcessedCount(nonNegative(result.processedCount()));
        run.setUpdatedCount(nonNegative(result.updatedCount()));
        run.setSuccessCount(nonNegative(result.successCount()));
        run.setFailureCount(nonNegative(result.failureCount()));
        run.setSummary(safeSummary(result.summary()));
        run.setErrorCode(result.failureCount() > 0 ? "PARTIAL_FAILURE" : null);
        run.setStatus(result.failureCount() > 0
                ? JobRunStatus.COMPLETED_WITH_WARNINGS
                : JobRunStatus.COMPLETED);
        return repository.saveAndFlush(run);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(UUID runId) {
        BackgroundJobRun run = repository.findById(runId).orElseThrow();
        run.setFinishedAt(Instant.now());
        run.setStatus(JobRunStatus.FAILED);
        run.setFailureCount(Math.max(1, run.getFailureCount()));
        run.setErrorCode("EXECUTION_FAILED");
        run.setSummary(null);
        repository.saveAndFlush(run);
    }

    private int nonNegative(int value) {
        return Math.max(0, value);
    }

    private String safeSummary(String summary) {
        if (summary == null || summary.isBlank()) return null;
        return summary.length() <= 300 ? summary : summary.substring(0, 300);
    }

    public record JobRunResult(int processedCount, int updatedCount, int successCount,
                               int failureCount, String summary) {
        public static JobRunResult empty() {
            return new JobRunResult(0, 0, 0, 0, null);
        }

        public static JobRunResult completed(int processed, int updated, int successful,
                                             int failed, String summary) {
            return new JobRunResult(processed, updated, successful, failed, summary);
        }
    }
}
