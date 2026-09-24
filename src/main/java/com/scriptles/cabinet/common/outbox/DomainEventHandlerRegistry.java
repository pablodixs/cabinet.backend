package com.scriptles.cabinet.common.outbox;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class DomainEventHandlerRegistry {
    private final Map<DomainEventType, List<DomainEventHandler>> handlers;

    public DomainEventHandlerRegistry(List<DomainEventHandler> registeredHandlers) {
        Map<DomainEventType, List<DomainEventHandler>> collected = new EnumMap<>(DomainEventType.class);
        for (DomainEventHandler handler : registeredHandlers) {
            for (DomainEventType eventType : handler.supportedEventTypes()) {
                collected.computeIfAbsent(eventType, ignored -> new java.util.ArrayList<>()).add(handler);
            }
        }
        this.handlers = collected.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> List.copyOf(e.getValue())));
        for (DomainEventType eventType : DomainEventType.values()) {
            if (!this.handlers.containsKey(eventType)) {
                throw new IllegalStateException("No handler registered for domain event " + eventType);
            }
        }
    }

    public void dispatch(DomainOutboxEvent event) {
        List<DomainEventHandler> matching = handlers.get(event.getEventType());
        if (matching == null || matching.isEmpty()) {
            throw new IllegalStateException("No handler registered for domain event " + event.getEventType());
        }
        matching.forEach(handler -> handler.handle(event));
    }
}
