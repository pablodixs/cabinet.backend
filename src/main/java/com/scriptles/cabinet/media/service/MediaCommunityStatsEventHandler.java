package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.common.outbox.DomainEventHandler;
import com.scriptles.cabinet.common.outbox.DomainEventType;
import com.scriptles.cabinet.common.outbox.DomainOutboxEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class MediaCommunityStatsEventHandler implements DomainEventHandler {
    private static final Set<DomainEventType> SUPPORTED = EnumSet.of(
            DomainEventType.MEDIA_LIKED,
            DomainEventType.MEDIA_UNLIKED,
            DomainEventType.RATING_CREATED,
            DomainEventType.RATING_UPDATED,
            DomainEventType.RATING_REMOVED,
            DomainEventType.MEDIA_COMPLETED,
            DomainEventType.MEDIA_UNCOMPLETED,
            DomainEventType.LIST_ITEM_ADDED,
            DomainEventType.LIST_ITEM_REMOVED
    );

    private final MediaCommunityStatsRebuilder rebuilder;

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
