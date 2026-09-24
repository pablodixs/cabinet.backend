package com.scriptles.cabinet.common.outbox;

import lombok.extern.slf4j.Slf4j;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Component
@Slf4j
public class DomainOutboxWorker {
    private final DomainOutboxRepository repository;
    private final DomainOutboxClaimService claimService;
    private final DomainOutboxDispatcher dispatcher;
    private final ThreadPoolTaskExecutor executor;
    private final Duration lockTimeout;
    private final int maxAttempts;
    private final Duration baseBackoff;
    private final Duration maxBackoff;
    private final MeterRegistry meters;

    public DomainOutboxWorker(
            DomainOutboxRepository repository,
            DomainOutboxClaimService claimService,
            DomainOutboxDispatcher dispatcher,
            @Qualifier("domainOutboxTaskExecutor") ThreadPoolTaskExecutor executor,
            @Value("${domain.outbox.lock-timeout:5m}") Duration lockTimeout,
            @Value("${domain.outbox.max-attempts:8}") int maxAttempts,
            @Value("${domain.outbox.base-backoff:2s}") Duration baseBackoff,
            @Value("${domain.outbox.max-backoff:5m}") Duration maxBackoff,
            MeterRegistry meters
    ) {
        this.repository = repository;
        this.claimService = claimService;
        this.dispatcher = dispatcher;
        this.executor = executor;
        this.lockTimeout = lockTimeout;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.baseBackoff = baseBackoff;
        this.maxBackoff = maxBackoff;
        this.meters = meters;
    }

    @Scheduled(fixedDelayString = "${domain.outbox.poll-delay:1000}")
    public void poll() {
        int released = claimService.releaseStale(lockTimeout, maxAttempts);
        if (released > 0) log.warn("Released {} stale domain outbox event(s)", released);
        int capacity = Math.max(0, executor.getMaxPoolSize() - executor.getActiveCount());
        if (capacity == 0) return;

        String workerId = ManagementFactory.getRuntimeMXBean().getName();
        for (DomainOutboxClaimService.ClaimedEvent claim : claimService.claim(workerId, capacity)) {
            try {
                executor.execute(() -> process(claim));
            } catch (RuntimeException submissionFailure) {
                claimService.release(claim.eventId(), claim.claimToken());
                log.warn("Unable to submit domain outbox event {}", claim.eventId(), submissionFailure);
            }
        }
    }

    void process(DomainOutboxClaimService.ClaimedEvent claim) {
        UUID eventId = claim.eventId();
        DomainOutboxEvent event = repository.findById(eventId).orElse(null);
        if (event == null || event.getStatus() != DomainOutboxStatus.PROCESSING
                || !claim.claimToken().equals(event.getLockedBy())) return;
        String eventType = event.getEventType().name();
        Timer.Sample sample = Timer.start(meters);
        try {
            dispatcher.dispatch(event);
            claimService.complete(eventId, claim.claimToken());
            meters.counter("cabinet.outbox.event.total", "queue", "domain", "event_type", eventType,
                    "outcome", "completed").increment();
        } catch (RuntimeException failure) {
            boolean retrying = claimService.retry(eventId, claim.claimToken(), failure, maxAttempts,
                    nextBackoff(event.getAttemptCount()));
            meters.counter("cabinet.outbox.event.failure", "queue", "domain", "event_type", eventType)
                    .increment();
            meters.counter("cabinet.outbox.event.total", "queue", "domain", "event_type", eventType,
                    "outcome", retrying ? "retry" : "dead").increment();
            if (!retrying) {
                log.error("Domain outbox event {} moved to DEAD after failure: {}", eventId, failure.getMessage());
            } else {
                log.warn("Domain outbox event {} failed and will retry: {}", eventId, failure.getMessage());
            }
        } finally {
            sample.stop(meters.timer("cabinet.outbox.processing.duration", "queue", "domain",
                    "event_type", eventType));
        }
    }

    private Duration nextBackoff(int previousAttempts) {
        int exponent = Math.min(previousAttempts, 30);
        long multiplier = 1L << exponent;
        long baseMillis = Math.max(1, baseBackoff.toMillis());
        long capMillis = Math.max(baseMillis, maxBackoff.toMillis());
        long exponential = baseMillis > capMillis / multiplier ? capMillis : Math.min(capMillis, baseMillis * multiplier);
        long jitter = ThreadLocalRandom.current().nextLong(Math.max(1, exponential / 2 + 1));
        return Duration.ofMillis(Math.min(capMillis, exponential / 2 + jitter));
    }
}
