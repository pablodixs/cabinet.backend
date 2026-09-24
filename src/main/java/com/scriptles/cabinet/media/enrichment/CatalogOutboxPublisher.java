package com.scriptles.cabinet.media.enrichment;

import com.scriptles.cabinet.media.entity.CatalogOutboxEvent;
import com.scriptles.cabinet.media.catalog.CatalogEventPayload;
import com.scriptles.cabinet.media.enums.*;
import com.scriptles.cabinet.media.repository.CatalogOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.EnumSet;
import java.util.UUID;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CatalogOutboxPublisher {
    private static final EnumSet<CatalogOutboxStatus> ACTIVE = EnumSet.of(
            CatalogOutboxStatus.PENDING,
            CatalogOutboxStatus.PROCESSING,
            CatalogOutboxStatus.RETRY
    );

    private final CatalogOutboxRepository repository;

    public void publishCoreReady(
            UUID mediaId,
            ExternalSource source,
            String externalId,
            MediaType mediaType,
            String locale
    ) {
        if (repository.existsByAggregateIdAndEventTypeAndStatusIn(
                mediaId, CatalogEventType.MEDIA_CORE_MATERIALIZED, ACTIVE)) {
            return;
        }
        CatalogOutboxEvent event = new CatalogOutboxEvent();
        event.setId(UUID.randomUUID());
        event.setAggregateId(mediaId);
        event.setEventType(CatalogEventType.MEDIA_CORE_MATERIALIZED);
        event.setPayload(new CatalogEventPayload(source, externalId, mediaType, locale,
                CatalogSyncReason.IMPORT_ENRICHMENT));
        event.setStatus(CatalogOutboxStatus.PENDING);
        event.setAvailableAt(Instant.now());
        repository.save(event);
    }

    public void publishAlbumReleaseVersionsSync(
            UUID mediaId,
            String releaseGroupId,
            String locale
    ) {
        if (repository.existsByAggregateIdAndEventTypeAndStatusIn(
                mediaId, CatalogEventType.ALBUM_RELEASE_VERSIONS_SYNC_REQUESTED, ACTIVE)) {
            return;
        }
        CatalogOutboxEvent event = new CatalogOutboxEvent();
        event.setId(UUID.randomUUID());
        event.setAggregateId(mediaId);
        event.setEventType(CatalogEventType.ALBUM_RELEASE_VERSIONS_SYNC_REQUESTED);
        event.setPayload(new CatalogEventPayload(
                ExternalSource.MUSICBRAINZ, releaseGroupId, MediaType.ALBUM, locale));
        event.setStatus(CatalogOutboxStatus.PENDING);
        event.setAvailableAt(Instant.now());
        repository.save(event);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishRefresh(
            UUID mediaId,
            ExternalSource source,
            String externalId,
            MediaType mediaType,
            String locale,
            CatalogSyncReason reason
    ) {
        if (repository.existsByAggregateIdAndEventTypeAndStatusIn(
                mediaId, CatalogEventType.MEDIA_REFRESH_REQUESTED, ACTIVE)) {
            return;
        }
        CatalogOutboxEvent event = new CatalogOutboxEvent();
        event.setId(UUID.randomUUID());
        event.setAggregateId(mediaId);
        event.setEventType(CatalogEventType.MEDIA_REFRESH_REQUESTED);
        event.setPayload(new CatalogEventPayload(source, externalId, mediaType, locale, reason));
        event.setStatus(CatalogOutboxStatus.PENDING);
        event.setAvailableAt(Instant.now());
        repository.save(event);
    }
}
