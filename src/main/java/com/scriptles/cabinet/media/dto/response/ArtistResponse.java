package com.scriptles.cabinet.media.dto.response;

import com.scriptles.cabinet.media.enums.CreditRole;
import com.scriptles.cabinet.media.enums.ExternalSource;

import java.util.List;
import java.util.UUID;

public record ArtistResponse(
        UUID id,
        String name,
        String biography,
        String imageUrl,
        ExternalSource source,
        String externalId,
        long workCount,
        List<CreditRole> roles
) {
}
