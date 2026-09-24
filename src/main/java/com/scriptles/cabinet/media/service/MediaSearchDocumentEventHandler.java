package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.common.outbox.DomainEventHandler;
import com.scriptles.cabinet.common.outbox.DomainEventType;
import com.scriptles.cabinet.common.outbox.DomainOutboxEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class MediaSearchDocumentEventHandler implements DomainEventHandler {
    private static final Set<DomainEventType> SUPPORTED = Set.of(
            DomainEventType.MEDIA_IMPORTED,
            DomainEventType.MEDIA_METADATA_CHANGED
    );

    private final MediaSearchDocumentRebuilder rebuilder;

    @Override
    public Set<DomainEventType> supportedEventTypes() {
        return SUPPORTED;
    }

    @Override
    public void handle(DomainOutboxEvent event) {
        if (!"MEDIA".equals(event.getAggregateType())) {
            throw new IllegalArgumentException("Expected MEDIA aggregate for " + event.getEventType());
        }
        rebuilder.rebuild(event.getAggregateId());
    }
}
