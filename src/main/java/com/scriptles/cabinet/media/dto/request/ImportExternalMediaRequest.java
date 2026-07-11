package com.scriptles.cabinet.media.dto.request;

import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ImportExternalMediaRequest(
        @NotNull ExternalSource source,
        @NotBlank String externalId,
        @NotNull MediaType mediaType
) {
}
