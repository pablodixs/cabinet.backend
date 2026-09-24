package com.scriptles.cabinet.common.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DomainOutboxClaimService {
    private final DomainOutboxRepository repository;

    @Transactional
    public List<ClaimedEvent> claim(String workerId, int limit) {
        if (limit <= 0) return List.of();
        Instant now = Instant.now();
        List<DomainOutboxEvent> events = repository.claimable(now, PageRequest.of(0, limit));
        return events.stream().map(event -> {
            String claimToken = workerId + ":" + UUID.randomUUID();
            event.setStatus(DomainOutboxStatus.PROCESSING);
            event.setProcessingStartedAt(now);
            event.setLockedBy(claimToken);
            return new ClaimedEvent(event.getId(), claimToken);
        }).toList();
    }

    @Transactional
    public int releaseStale(Duration lockTimeout, int maxAttempts) {
        Instant now = Instant.now();
        return repository.releaseStaleProcessing(now.minus(lockTimeout), now, maxAttempts);
    }

    @Transactional
    public void release(UUID eventId, String claimToken) {
        repository.findByIdForUpdate(eventId).ifPresent(event -> {
            if (!ownsClaim(event, claimToken)) return;
            event.setStatus(DomainOutboxStatus.RETRY);
            event.setAvailableAt(Instant.now());
            clearLock(event);
        });
    }

    @Transactional
    public void complete(UUID eventId, String claimToken) {
        repository.findByIdForUpdate(eventId).ifPresent(event -> {
            if (!ownsClaim(event, claimToken)) return;
            event.setStatus(DomainOutboxStatus.COMPLETED);
            event.setProcessedAt(Instant.now());
            event.setLastError(null);
            clearLock(event);
        });
    }

    @Transactional
    public boolean retry(UUID eventId, String claimToken, Throwable failure, int maxAttempts, Duration delay) {
        DomainOutboxEvent event = repository.findByIdForUpdate(eventId).orElse(null);
        if (event == null || !ownsClaim(event, claimToken)) return false;
        int attempts = event.getAttemptCount() + 1;
        event.setAttemptCount(attempts);
        String message = failure.getMessage() == null
                ? failure.getClass().getSimpleName()
                : failure.getMessage();
        event.setLastError(truncate(message, 2_000));
        clearLock(event);
        if (attempts >= maxAttempts) {
            event.setStatus(DomainOutboxStatus.DEAD);
            return false;
        }
        event.setStatus(DomainOutboxStatus.RETRY);
        event.setAvailableAt(Instant.now().plus(delay));
        return true;
    }

    private void clearLock(DomainOutboxEvent event) {
        event.setProcessingStartedAt(null);
        event.setLockedBy(null);
    }

    private boolean ownsClaim(DomainOutboxEvent event, String claimToken) {
        return event.getStatus() == DomainOutboxStatus.PROCESSING
                && claimToken.equals(event.getLockedBy());
    }

    private String truncate(String value, int size) {
        if (value == null || value.length() <= size) return value;
        return value.substring(0, size);
    }

    public record ClaimedEvent(UUID eventId, String claimToken) {
    }
}
