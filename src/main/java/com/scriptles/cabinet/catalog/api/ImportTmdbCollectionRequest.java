package com.scriptles.cabinet.catalog.api;

import jakarta.validation.constraints.NotBlank;

public record ImportTmdbCollectionRequest(
        String provider,
        @NotBlank String externalId,
        String locale
) {
}
