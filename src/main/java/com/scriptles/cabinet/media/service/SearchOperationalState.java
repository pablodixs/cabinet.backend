package com.scriptles.cabinet.media.service;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class SearchOperationalState {
    private static final Duration FAILURE_WINDOW = Duration.ofMinutes(5);
    private final AtomicReference<Instant> lastFailure = new AtomicReference<>();
    private final AtomicReference<Instant> lastSuccess = new AtomicReference<>();

    public void recordSuccess() {
        lastSuccess.set(Instant.now());
    }

    public void recordFailure() {
        lastFailure.set(Instant.now());
    }

    public boolean degraded() {
        Instant failure = lastFailure.get();
        if (failure == null || failure.isBefore(Instant.now().minus(FAILURE_WINDOW))) return false;
        Instant success = lastSuccess.get();
        return success == null || success.isBefore(failure);
    }
}
