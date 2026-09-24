package com.scriptles.cabinet.common.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Map;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DomainOutboxPublisher {
    private final DomainOutboxRepository repository;
    private final List<DomainOutboxWriteObserver> writeObservers;

    public void publish(
            DomainEventType eventType,
            String aggregateType,
            UUID aggregateId,
            Map<String, Object> payload
    ) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Domain outbox events must be written in a domain transaction");
        }
        DomainOutboxEvent event = new DomainOutboxEvent();
        event.setId(UUID.randomUUID());
        event.setEventType(eventType);
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setPayload(payload == null ? Map.of() : Map.copyOf(payload));
        event.setStatus(DomainOutboxStatus.PENDING);
        event.setAttemptCount(0);
        event.setAvailableAt(Instant.now());
        repository.save(event);
        writeObservers.forEach(observer -> observer.onPublished(eventType, aggregateType, aggregateId));
    }

    public void publishMediaEvent(DomainEventType eventType, UUID mediaId, Map<String, Object> payload) {
        publish(eventType, "MEDIA", mediaId, payload);
    }
}
