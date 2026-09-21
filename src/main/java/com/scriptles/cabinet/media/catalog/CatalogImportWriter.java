package com.scriptles.cabinet.media.catalog;

import com.scriptles.cabinet.media.dto.request.MediaTarget;
import com.scriptles.cabinet.media.enrichment.CatalogOutboxPublisher;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.CatalogStatus;
import com.scriptles.cabinet.profile.service.CatalogOrganizationDiscoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CatalogImportWriter {
    private final CatalogMaterializationService materializationService;
    private final CatalogOutboxPublisher outboxPublisher;
    private final CatalogOrganizationDiscoveryService organizationDiscovery;

    @Transactional
    public Media materialize(MediaTarget target, CatalogResolver.Resolution resolution) {
        Media media = materializationService.findOrCreateCore(target, resolution);
        if (target.source() != null && !organizationDiscovery.hasLinks(media)) {
            organizationDiscovery.discover(media, target.source(), target.externalId());
        }
        publishEnrichmentIfRequired(media, target, resolution);
        return media;
    }

    @Transactional
    public Media materializeSeed(MediaTarget target, CatalogResolver.Resolution resolution) {
        Media media = materializationService.findOrCreateCore(target, resolution);
        // Collection seeds already contain enough data for CORE_READY. Provider-specific secondary
        // lookups belong to the committed CatalogOutbox pipeline, not this materialization step.
        publishEnrichmentIfRequired(media, target, resolution);
        return media;
    }

    private void publishEnrichmentIfRequired(Media media, MediaTarget target,
                                             CatalogResolver.Resolution resolution) {
        if (target.source() != null && media.getCatalogStatus() != CatalogStatus.READY) {
            outboxPublisher.publishCoreReady(
                    media.getId(),
                    target.source(),
                    target.externalId(),
                    target.mediaType(),
                    resolution.locale()
            );
        }
    }
}
