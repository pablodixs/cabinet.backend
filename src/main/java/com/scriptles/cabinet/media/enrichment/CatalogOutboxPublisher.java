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
        event.setPayload(new CatalogEventPayload(source, externalId, mediaType, locale));
        event.setStatus(CatalogOutboxStatus.PENDING);
        event.setAvailableAt(Instant.now());
        repository.save(event);
    }
}
