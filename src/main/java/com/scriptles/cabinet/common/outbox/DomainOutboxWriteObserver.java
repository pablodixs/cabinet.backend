package com.scriptles.cabinet.common.outbox;

import java.util.UUID;

public interface DomainOutboxWriteObserver {
    void onPublished(DomainEventType eventType, String aggregateType, UUID aggregateId);
}
