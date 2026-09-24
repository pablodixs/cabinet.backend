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
public class MediaRankingSnapshotEventHandler implements DomainEventHandler {
    private static final Set<DomainEventType> SUPPORTED = EnumSet.of(
            DomainEventType.MEDIA_LIKED,
            DomainEventType.MEDIA_UNLIKED,
            DomainEventType.RATING_CREATED,
            DomainEventType.RATING_UPDATED,
            DomainEventType.RATING_REMOVED,
            DomainEventType.MEDIA_COMPLETED,
            DomainEventType.MEDIA_UNCOMPLETED,
            DomainEventType.LIST_ITEM_ADDED,
            DomainEventType.LIST_ITEM_REMOVED,
            DomainEventType.REVIEW_CREATED,
            DomainEventType.REVIEW_UPDATED,
            DomainEventType.REVIEW_REMOVED,
            DomainEventType.DIARY_ENTRY_CREATED,
            DomainEventType.DIARY_ENTRY_UPDATED,
            DomainEventType.DIARY_ENTRY_REMOVED
    );

    private final MediaRankingSnapshotRebuilder rebuilder;

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
