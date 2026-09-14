package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.media.enrichment.CatalogEnrichmentPersistenceService;
import com.scriptles.cabinet.media.external.ExternalMedia;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** Shared metadata persistence boundary used by initial enrichment and refreshes. */
@Service
@RequiredArgsConstructor
public class CatalogMetadataPersistenceService {
    private final CatalogEnrichmentPersistenceService enrichmentPersistenceService;

    public void update(UUID mediaId, ExternalMedia external, String locale, String wikidataId) {
        enrichmentPersistenceService.complete(mediaId, external, locale, wikidataId);
    }

    public void updateTranslation(UUID mediaId, ExternalMedia external, String locale) {
        enrichmentPersistenceService.saveTranslation(mediaId, external, locale);
    }
}
