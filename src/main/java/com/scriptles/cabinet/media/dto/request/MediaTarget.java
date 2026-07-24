package com.scriptles.cabinet.media.dto.request;

import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.enums.SupportedLocale;
import jakarta.validation.constraints.AssertTrue;

import java.util.UUID;

public record MediaTarget(
        UUID mediaId,
        ExternalSource source,
        String externalId,
        MediaType mediaType,
        String locale
) {
    @AssertTrue(message = "Informe mediaId ou source, externalId e mediaType")
    public boolean isValidReference() {
        boolean local = mediaId != null;
        boolean external = source != null && externalId != null && !externalId.isBlank() && mediaType != null;
        return local ^ external;
    }

    public String normalizedLocale() {
        return SupportedLocale.from(locale).tag();
    }
}
