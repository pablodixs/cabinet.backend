package com.scriptles.cabinet.media.catalog;

import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;

public record CatalogEventPayload(
        ExternalSource source,
        String externalId,
        MediaType mediaType,
        String locale
) {
}
