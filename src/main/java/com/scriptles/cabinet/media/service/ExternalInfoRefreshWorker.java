package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.ExternalInfoKind;
import com.scriptles.cabinet.media.enums.ExternalInfoSnapshotStatus;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.external.ExternalAvailability;
import com.scriptles.cabinet.media.external.MusicBrainzClient;
import com.scriptles.cabinet.media.external.OmdbClient;
import com.scriptles.cabinet.media.external.TmdbClient;
import com.scriptles.cabinet.media.repository.ExternalReferenceRepository;
import com.scriptles.cabinet.media.repository.MediaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExternalInfoRefreshWorker {
    private static final Duration VIDEO_AVAILABILITY_TTL = Duration.ofHours(24);
    private static final Duration MUSIC_AVAILABILITY_TTL = Duration.ofDays(7);
    private static final Duration RECENT_RATING_TTL = Duration.ofHours(12);
    private static final Duration OLD_RATING_TTL = Duration.ofDays(7);
    private static final Duration FAILURE_RETRY = Duration.ofHours(1);

    private final MediaRepository mediaRepository;
    private final ExternalReferenceRepository externalReferenceRepository;
    private final TmdbClient tmdbClient;
    private final MusicBrainzClient musicBrainzClient;
    private final OmdbClient omdbClient;
    private final ExternalInfoPersistenceService persistenceService;

    public void refreshAvailability(UUID mediaId, String countryCode) {
        Media media = mediaRepository.findById(mediaId).orElse(null);
        if (media == null) {
            return;
        }

        try {
            ExternalAvailability availability;
            Duration ttl;
            if (media.getType() == MediaType.MOVIE || media.getType() == MediaType.SERIES) {
                String tmdbId = referenceId(mediaId, ExternalSource.TMDB)
                        .orElseThrow(() -> new IllegalStateException("TMDB reference missing"));
                availability = tmdbClient.findWatchProviders(media.getType(), tmdbId, countryCode);
                ttl = VIDEO_AVAILABILITY_TTL;
            } else if (media.getType() == MediaType.ALBUM || media.getType() == MediaType.TRACK) {
                String musicBrainzId = referenceId(mediaId, ExternalSource.MUSICBRAINZ)
                        .orElseThrow(() -> new IllegalStateException("MusicBrainz reference missing"));
                availability = musicBrainzClient.findListenAndBuyLinks(media.getType(), musicBrainzId);
                ttl = MUSIC_AVAILABILITY_TTL;
            } else {
                return;
            }
            persistenceService.replaceAvailability(mediaId, countryCode, availability, ttl);
        } catch (RuntimeException exception) {
            log.warn("Unable to refresh availability for media {}: {}", mediaId, exception.getMessage());
            persistenceService.markUnavailable(
                    mediaId,
                    ExternalInfoKind.AVAILABILITY,
                    countryCode,
                    ExternalInfoSnapshotStatus.ERROR,
                    errorCode(exception),
                    FAILURE_RETRY
            );
        }
    }

    public void refreshRatings(UUID mediaId) {
        Media media = mediaRepository.findById(mediaId).orElse(null);
        if (media == null || (media.getType() != MediaType.MOVIE && media.getType() != MediaType.SERIES)) {
            return;
        }
        if (!omdbClient.isConfigured()) {
            persistenceService.markUnavailable(
                    mediaId,
                    ExternalInfoKind.RATINGS,
                    MediaExternalInfoService.GLOBAL_REGION,
                    ExternalInfoSnapshotStatus.NOT_CONFIGURED,
                    "OMDB_NOT_CONFIGURED",
                    FAILURE_RETRY
            );
            return;
        }

        try {
            String imdbId = resolveImdbId(mediaId, media.getType());
            if (imdbId == null) {
                persistenceService.replaceRatings(mediaId, null, java.util.List.of(), ratingTtl(media));
                return;
            }
            persistenceService.replaceRatings(
                    mediaId,
                    imdbId,
                    omdbClient.findRatings(imdbId),
                    ratingTtl(media)
            );
        } catch (RuntimeException exception) {
            log.warn("Unable to refresh ratings for media {}: {}", mediaId, exception.getMessage());
            persistenceService.markUnavailable(
                    mediaId,
                    ExternalInfoKind.RATINGS,
                    MediaExternalInfoService.GLOBAL_REGION,
                    ExternalInfoSnapshotStatus.ERROR,
                    errorCode(exception),
                    FAILURE_RETRY
            );
        }
    }

    private String resolveImdbId(UUID mediaId, MediaType mediaType) {
        Optional<String> stored = referenceId(mediaId, ExternalSource.IMDB);
        if (stored.isPresent()) {
            return stored.get();
        }
        String tmdbId = referenceId(mediaId, ExternalSource.TMDB)
                .orElseThrow(() -> new IllegalStateException("TMDB reference missing"));
        return tmdbClient.findImdbId(mediaType, tmdbId)
                .map(value -> persistenceService.storeImdbReference(mediaId, value))
                .orElse(null);
    }

    private Optional<String> referenceId(UUID mediaId, ExternalSource source) {
        return externalReferenceRepository.findByMediaIdAndSource(mediaId, source)
                .map(ExternalReference::getExternalId)
                .filter(value -> value != null && !value.isBlank());
    }

    private Duration ratingTtl(Media media) {
        LocalDate releaseDate = media.getReleaseDate();
        return releaseDate != null && releaseDate.isAfter(LocalDate.now().minusDays(60))
                ? RECENT_RATING_TTL
                : OLD_RATING_TTL;
    }

    private String errorCode(RuntimeException exception) {
        if (exception instanceof IllegalStateException) {
            return "REFERENCE_MISSING";
        }
        return "PROVIDER_UNAVAILABLE";
    }
}
