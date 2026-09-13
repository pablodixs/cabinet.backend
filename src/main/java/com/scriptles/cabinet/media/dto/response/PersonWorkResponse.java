package com.scriptles.cabinet.media.dto.response;

import com.scriptles.cabinet.media.enums.CreditRole;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PersonWorkResponse(
        UUID id,
        String externalId,
        ExternalSource source,
        MediaType type,
        String title,
        String coverUrl,
        LocalDate releaseDate,
        boolean imported,
        List<CreditResponse> credits
) {
    public record CreditResponse(
            CreditRole role,
            String characterName
    ) {
    }
}
