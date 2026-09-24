package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.common.outbox.DomainEventType;
import com.scriptles.cabinet.common.outbox.DomainOutboxWriteObserver;
import com.scriptles.cabinet.media.repository.MediaRankingSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MediaRankingSnapshotDirtyObserver implements DomainOutboxWriteObserver {
    private static final Set<DomainEventType> RANKING_EVENTS = EnumSet.of(
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

    private final MediaRankingSnapshotRepository repository;

    @Override
    public void onPublished(DomainEventType eventType, String aggregateType, UUID aggregateId) {
        if ("MEDIA".equals(aggregateType) && RANKING_EVENTS.contains(eventType)) {
            repository.markDirty(aggregateId);
        }
    }
}
