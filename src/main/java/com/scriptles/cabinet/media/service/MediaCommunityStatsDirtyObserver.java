package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.common.outbox.DomainEventType;
import com.scriptles.cabinet.common.outbox.DomainOutboxWriteObserver;
import com.scriptles.cabinet.media.repository.MediaCommunityStatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MediaCommunityStatsDirtyObserver implements DomainOutboxWriteObserver {
    private static final Set<DomainEventType> COMMUNITY_STATS_EVENTS = EnumSet.of(
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

    private final MediaCommunityStatsRepository repository;

    @Override
    public void onPublished(DomainEventType eventType, String aggregateType, UUID aggregateId) {
        if ("MEDIA".equals(aggregateType) && COMMUNITY_STATS_EVENTS.contains(eventType)) {
            repository.markDirty(aggregateId);
        }
    }
}
