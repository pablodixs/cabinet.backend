package com.scriptles.cabinet.catalog.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SetTmdbCollectionReferenceRequest(
        @NotBlank @Size(max = 255) String externalId
) {
}
