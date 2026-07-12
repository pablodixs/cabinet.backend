package com.scriptles.cabinet.media.dto.response;

import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;

import java.time.LocalDate;
import java.util.UUID;

public record ExternalMediaResponse(
        UUID id,
        String externalId,
        ExternalSource source,
        MediaType type,
        String title,
        String creator,
        String description,
        String coverUrl,
        LocalDate releaseDate,
        boolean imported
) {
}
