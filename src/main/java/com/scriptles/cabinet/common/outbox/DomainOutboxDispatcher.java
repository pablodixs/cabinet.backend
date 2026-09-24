package com.scriptles.cabinet.common.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DomainOutboxDispatcher {
    private final DomainEventHandlerRegistry registry;

    public void dispatch(DomainOutboxEvent event) {
        registry.dispatch(event);
    }
}
