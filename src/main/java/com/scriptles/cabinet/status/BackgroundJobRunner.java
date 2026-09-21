package com.scriptles.cabinet.status;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class BackgroundJobRunner {
    private final BackgroundJobTracker tracker;

    public BackgroundJobRun execute(JobKey jobKey, Supplier<BackgroundJobTracker.JobRunResult> work) {
        UUID runId = tracker.start(jobKey);
        try {
            BackgroundJobTracker.JobRunResult result = work.get();
            return tracker.complete(runId,
                    result == null ? BackgroundJobTracker.JobRunResult.empty() : result);
        } catch (RuntimeException failure) {
            tracker.fail(runId);
            throw failure;
        }
    }
}
