package com.scriptles.cabinet.media.external;

import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.CreditRole;
import com.scriptles.cabinet.media.enums.MediaType;

import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public record ExternalMedia(
        ExternalSource source,
        String externalId,
        MediaType type,
        String title,
        String originalTitle,
        String description,
        String tagline,
        String coverUrl,
        String externalUrl,
        String wikidataId,
        LocalDate releaseDate,
        String originalLanguage,
        String countryCode,
        String isbn10,
        String isbn13,
        Integer pageCount,
        String publisher,
        Integer durationSeconds,
        Boolean explicit,
        Integer runtimeMinutes,
        Long budget,
        Long revenue,
        String seriesStatus,
        Integer numberOfSeasons,
        Integer numberOfEpisodes,
        LocalDate lastAirDate,
        String albumType,
        Integer numberOfTracks,
        String creator,
        String backdropUrl,
        String logoUrl,
        List<ExternalGenre> genres,
        List<ExternalTrack> tracks,
        List<ExternalSeason> seasons,
        List<ExternalCredit> credits
) {
    public ExternalMedia withCreator(String value) {
        return new ExternalMedia(
                source, externalId, type, title, originalTitle, description, tagline, coverUrl, externalUrl,
                wikidataId, releaseDate, originalLanguage, countryCode, isbn10, isbn13, pageCount, publisher,
                durationSeconds, explicit,
                runtimeMinutes, budget, revenue, seriesStatus, numberOfSeasons, numberOfEpisodes,
                lastAirDate, albumType, numberOfTracks, value, backdropUrl, logoUrl, genres, tracks, seasons, credits
        );
    }

    public ExternalMedia withCoverUrl(String value) {
        return new ExternalMedia(
                source, externalId, type, title, originalTitle, description, tagline, value, externalUrl,
                wikidataId, releaseDate, originalLanguage, countryCode, isbn10, isbn13, pageCount, publisher,
                durationSeconds, explicit,
                runtimeMinutes, budget, revenue, seriesStatus, numberOfSeasons, numberOfEpisodes,
                lastAirDate, albumType, numberOfTracks, creator, backdropUrl, logoUrl, genres, tracks, seasons, credits
        );
    }

    public ExternalMedia withEnrichment(String enrichedLogoUrl, List<ExternalGenre> enrichedGenres) {
        return new ExternalMedia(
                source, externalId, type, title, originalTitle, description, tagline, coverUrl, externalUrl,
                wikidataId, releaseDate, originalLanguage, countryCode, isbn10, isbn13, pageCount, publisher,
                durationSeconds, explicit,
                runtimeMinutes, budget, revenue, seriesStatus, numberOfSeasons, numberOfEpisodes,
                lastAirDate, albumType, numberOfTracks, creator, backdropUrl,
                logoUrl != null ? logoUrl : enrichedLogoUrl,
                mergeGenres(genres, enrichedGenres), tracks, seasons, credits
        );
    }

    private static List<ExternalGenre> mergeGenres(List<ExternalGenre> primary, List<ExternalGenre> enrichment) {
        Map<String, ExternalGenre> values = new LinkedHashMap<>();
        if (primary != null) {
            primary.forEach(genre -> values.put(genre.name().toLowerCase(), genre));
        }
        if (enrichment != null) {
            enrichment.forEach(genre -> values.putIfAbsent(genre.name().toLowerCase(), genre));
        }
        return new ArrayList<>(values.values());
    }

    public record ExternalGenre(String id, String name, ExternalSource source) {
    }

    public record ExternalTrack(String externalId, String title, Integer discNumber, Integer trackNumber,
                                Integer durationSeconds, Boolean explicit) {
    }

    public record ExternalSeason(String externalId, Integer seasonNumber, String name, String description,
                                 String coverUrl, Integer episodeCount, LocalDate airDate) {
    }

    public record ExternalEpisode(String externalId, Integer episodeNumber, String title, String description,
                                  String stillUrl, LocalDate airDate, Integer runtimeMinutes) {
    }

    public record ExternalCredit(
            String externalId,
            String name,
            CreditRole role,
            String characterName,
            Integer position,
            String imageUrl,
            ExternalSource source
    ) {
    }
}
