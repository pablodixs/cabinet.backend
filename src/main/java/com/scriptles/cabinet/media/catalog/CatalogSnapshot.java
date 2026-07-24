package com.scriptles.cabinet.media.catalog;

import com.scriptles.cabinet.media.external.ExternalMedia;

import java.time.Instant;

public record CatalogSnapshot(
        ExternalMedia media,
        String locale,
        Instant fetchedAt
) {
}
