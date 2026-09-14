package com.scriptles.cabinet.media.catalog;

import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.enums.CatalogSyncReason;

public record CatalogEventPayload(
        ExternalSource source,
        String externalId,
        MediaType mediaType,
        String locale,
        CatalogSyncReason reason
) {
    public CatalogEventPayload(
            ExternalSource source,
            String externalId,
            MediaType mediaType,
            String locale
    ) {
        this(source, externalId, mediaType, locale, CatalogSyncReason.IMPORT_ENRICHMENT);
    }
}
