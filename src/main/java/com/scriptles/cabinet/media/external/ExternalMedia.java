package com.scriptles.cabinet.media.external;

import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;

import java.time.LocalDate;

public record ExternalMedia(
        ExternalSource source,
        String externalId,
        MediaType type,
        String title,
        String originalTitle,
        String description,
        String coverUrl,
        String externalUrl,
        LocalDate releaseDate,
        String originalLanguage,
        String countryCode,
        String isbn10,
        String isbn13,
        Integer pageCount,
        String publisher,
        Integer runtimeMinutes,
        Long budget,
        Long revenue,
        String seriesStatus,
        Integer numberOfSeasons,
        Integer numberOfEpisodes,
        LocalDate lastAirDate
) {
}
