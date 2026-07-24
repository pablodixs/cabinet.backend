package com.scriptles.cabinet.media.enrichment;

import com.scriptles.cabinet.media.entity.CatalogOutboxEvent;
import com.scriptles.cabinet.media.enums.CatalogOutboxStatus;
import com.scriptles.cabinet.media.repository.CatalogOutboxRepository;
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
public class CatalogOutboxClaimService {
    private final CatalogOutboxRepository repository;

    @Transactional
    public List<UUID> claim(String workerId, int limit) {
        List<CatalogOutboxEvent> events = repository.claimable(Instant.now(), PageRequest.of(0, limit));
        Instant lockedAt = Instant.now();
        events.forEach(event -> {
            event.setStatus(CatalogOutboxStatus.PROCESSING);
            event.setLockedAt(lockedAt);
            event.setLockedBy(workerId);
        });
        return events.stream().map(CatalogOutboxEvent::getId).toList();
    }

    @Transactional
    public void complete(UUID eventId) {
        repository.findById(eventId).ifPresent(event -> {
            event.setStatus(CatalogOutboxStatus.PROCESSED);
            event.setProcessedAt(Instant.now());
            event.setLockedAt(null);
            event.setLockedBy(null);
        });
    }

    @Transactional
    public boolean retry(UUID eventId, RuntimeException failure) {
        CatalogOutboxEvent event = repository.findById(eventId).orElse(null);
        if (event == null) return false;
        int attempts = event.getAttempts() + 1;
        event.setAttempts(attempts);
        event.setLastError(truncate(failure.getMessage(), 2_000));
        event.setLockedAt(null);
        event.setLockedBy(null);
        if (attempts >= 6) {
            event.setStatus(CatalogOutboxStatus.DEAD);
            return false;
        }
        event.setStatus(CatalogOutboxStatus.RETRY);
        event.setAvailableAt(Instant.now().plus(backoff(attempts)));
        return true;
    }

    private Duration backoff(int attempts) {
        return switch (attempts) {
            case 1 -> Duration.ofSeconds(30);
            case 2 -> Duration.ofMinutes(2);
            case 3 -> Duration.ofMinutes(10);
            case 4 -> Duration.ofHours(1);
            default -> Duration.ofHours(6);
        };
    }

    private String truncate(String value, int size) {
        if (value == null || value.length() <= size) return value;
        return value.substring(0, size);
    }
}
