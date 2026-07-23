package com.scriptles.cabinet.media.dto.response;

import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PublicMediaDetailsResponse(
        UUID id,
        String externalId,
        ExternalSource source,
        MediaType type,
        String title,
        String originalTitle,
        String creator,
        String description,
        String tagline,
        String coverUrl,
        String backdropUrl,
        String logoUrl,
        String externalUrl,
        LocalDate releaseDate,
        String originalLanguage,
        String countryCode,
        String wikidataId,
        Map<String, String> externalReferences,
        List<ExternalMediaDetailsResponse.GenreResponse> genres,
        List<ExternalMediaDetailsResponse.CreditResponse> credits,
        boolean imported,
        Object details
) {
}
