package com.scriptles.cabinet.media.dto.response;

import com.scriptles.cabinet.media.enums.CreditRole;
import com.scriptles.cabinet.media.enums.MediaType;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ArtistWorkResponse(
        UUID mediaId,
        MediaType type,
        String title,
        String coverUrl,
        LocalDate releaseDate,
        List<CreditResponse> credits
) {
    public record CreditResponse(
            CreditRole role,
            String characterName
    ) {
    }
}
