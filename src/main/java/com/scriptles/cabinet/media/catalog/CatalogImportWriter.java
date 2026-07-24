package com.scriptles.cabinet.media.catalog;

import com.scriptles.cabinet.media.dto.request.MediaTarget;
import com.scriptles.cabinet.media.enrichment.CatalogOutboxPublisher;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.CatalogStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CatalogImportWriter {
    private final CatalogMaterializationService materializationService;
    private final CatalogOutboxPublisher outboxPublisher;

    @Transactional
    public Media materialize(MediaTarget target, CatalogResolver.Resolution resolution) {
        Media media = materializationService.findOrCreateCore(target, resolution);
        if (target.source() != null && media.getCatalogStatus() != CatalogStatus.READY) {
            outboxPublisher.publishCoreReady(
                    media.getId(),
                    target.source(),
                    target.externalId(),
                    target.mediaType(),
                    resolution.locale()
            );
        }
        return media;
    }
}
