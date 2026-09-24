package com.scriptles.cabinet.common.outbox;

import java.util.Set;

public interface DomainEventHandler {
    Set<DomainEventType> supportedEventTypes();

    void handle(DomainOutboxEvent event);
}
