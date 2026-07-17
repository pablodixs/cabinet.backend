package com.scriptles.cabinet.media.dto.response;

import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.MediaType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ModerationMediaResponse(
        UUID id,
        MediaType type,
        String title,
        String originalTitle,
        String description,
        String tagline,
        String coverUrl,
        String backdropUrl,
        String logoUrl,
        LocalDate releaseDate,
        String originalLanguage,
        String countryCode,
        List<String> genres,
        long version,
        Instant updatedAt
) {
    public static ModerationMediaResponse from(Media media) {
        return new ModerationMediaResponse(
                media.getId(), media.getType(), media.getTitle(), media.getOriginalTitle(),
                media.getDescription(), media.getTagline(), media.getCoverUrl(), media.getBackdropUrl(),
                media.getLogoUrl(), media.getReleaseDate(), media.getOriginalLanguage(), media.getCountryCode(),
                media.getGenres().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList(),
                media.getVersion(), media.getUpdatedAt()
        );
    }
}
