package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.SeriesDetails;
import com.scriptles.cabinet.media.entity.SeriesSeason;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.CatalogSyncReason;
import com.scriptles.cabinet.media.enums.SeriesStatus;
import com.scriptles.cabinet.media.external.ExternalMedia;
import com.scriptles.cabinet.media.external.TmdbClient;
import com.scriptles.cabinet.media.repository.ExternalReferenceRepository;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.media.repository.SeriesDetailsRepository;
import com.scriptles.cabinet.media.repository.SeriesSeasonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SeriesTrackingSyncService {
    private static final String LANGUAGE = "pt-BR";

    private final MediaRepository mediaRepository;
    private final SeriesDetailsRepository detailsRepository;
    private final SeriesSeasonRepository seasonRepository;
    private final ExternalReferenceRepository referenceRepository;
    private final TmdbClient tmdbClient;
    private final SeasonEpisodeService seasonEpisodeService;
    private final CatalogMetadataRefreshScheduler metadataRefreshScheduler;

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = "mediaDetails", key = "#seriesId + ':pt-BR'"),
            @CacheEvict(cacheNames = "mediaDetails", key = "#seriesId + ':en-US'")
    })
    public void synchronize(UUID seriesId) {
        ExternalReference reference = referenceRepository
                .findByMediaIdAndSource(seriesId, ExternalSource.TMDB)
                .orElseThrow(() -> new IllegalStateException("TMDB reference missing for series " + seriesId));
        TmdbClient.SeriesTrackingSnapshot snapshot = tmdbClient.findSeriesTrackingSnapshot(
                reference.getExternalId(), LANGUAGE);
        ExternalMedia external = snapshot.media();
        Media series = mediaRepository.findById(seriesId)
                .orElseThrow(() -> new IllegalStateException("Series not found: " + seriesId));
        metadataRefreshScheduler.schedule(seriesId, CatalogSyncReason.SERIES_TRACKING);
        upsertSeasons(series, external.seasons());
        reference.setLastSyncedAt(Instant.now());
        referenceRepository.save(reference);

        List<SeriesSeason> regularSeasons = seasonRepository
                .findAllBySeriesIdOrderBySeasonNumberAsc(seriesId).stream()
                .filter(season -> season.getSeasonNumber() != null && season.getSeasonNumber() > 0)
                .toList();
        Set<Integer> targetNumbers = targetSeasonNumbers(regularSeasons, snapshot);
        for (Integer seasonNumber : targetNumbers) {
            seasonEpisodeService.syncSeason(seriesId, seasonNumber, reference.getExternalId(), LANGUAGE);
        }
    }

    private void updateSeries(Media series, ExternalMedia external) {
        series.setTitle(external.title());
        series.setOriginalTitle(external.originalTitle());
        series.setDescription(external.description());
        series.setTagline(external.tagline());
        series.setCoverUrl(external.coverUrl());
        series.setBackdropUrl(external.backdropUrl());
        series.setLogoUrl(external.logoUrl());
        series.setReleaseDate(external.releaseDate());
        mediaRepository.save(series);
    }

    private void updateDetails(Media series, ExternalMedia external) {
        SeriesDetails details = detailsRepository.findById(series.getId()).orElseGet(() -> {
            SeriesDetails created = new SeriesDetails();
            created.setMedia(series);
            return created;
        });
        details.setStatus(seriesStatus(external.seriesStatus()));
        details.setNumberOfSeasons(external.numberOfSeasons());
        details.setNumberOfEpisodes(external.numberOfEpisodes());
        details.setFirstAirDate(external.releaseDate());
        details.setLastAirDate(external.lastAirDate());
        detailsRepository.save(details);
    }

    private void upsertSeasons(Media series, List<ExternalMedia.ExternalSeason> externalSeasons) {
        for (ExternalMedia.ExternalSeason external : externalSeasons) {
            SeriesSeason season = seasonRepository
                    .findBySeriesIdAndSeasonNumber(series.getId(), external.seasonNumber())
                    .orElseGet(() -> {
                        SeriesSeason created = new SeriesSeason();
                        created.setSeries(series);
                        created.setSeasonNumber(external.seasonNumber());
                        return created;
                    });
            season.setExternalId(external.externalId());
            season.setName(external.name());
            season.setDescription(external.description());
            season.setCoverUrl(external.coverUrl());
            season.setEpisodeCount(external.episodeCount());
            season.setAirDate(external.airDate());
            seasonRepository.save(season);
        }
        seasonRepository.flush();
    }

    private Set<Integer> targetSeasonNumbers(
            List<SeriesSeason> seasons,
            TmdbClient.SeriesTrackingSnapshot snapshot
    ) {
        Set<Integer> targets = new HashSet<>();
        seasons.stream()
                .filter(season -> season.getEpisodesSyncedAt() == null)
                .map(SeriesSeason::getSeasonNumber)
                .forEach(targets::add);
        seasons.stream()
                .sorted(Comparator.comparing(SeriesSeason::getSeasonNumber).reversed())
                .limit(2)
                .map(SeriesSeason::getSeasonNumber)
                .forEach(targets::add);
        if (snapshot.lastEpisodeSeasonNumber() != null && snapshot.lastEpisodeSeasonNumber() > 0) {
            targets.add(snapshot.lastEpisodeSeasonNumber());
        }
        if (snapshot.nextEpisodeSeasonNumber() != null && snapshot.nextEpisodeSeasonNumber() > 0) {
            targets.add(snapshot.nextEpisodeSeasonNumber());
        }
        return targets;
    }

    private SeriesStatus seriesStatus(String status) {
        if (status == null) return SeriesStatus.UNKNOWN;
        return switch (status.toUpperCase()) {
            case "PLANNED", "IN PRODUCTION", "POST PRODUCTION" -> SeriesStatus.PLANNED;
            case "RETURNING SERIES", "PILOT" -> SeriesStatus.AIRING;
            case "ENDED" -> SeriesStatus.ENDED;
            case "CANCELED" -> SeriesStatus.CANCELLED;
            default -> SeriesStatus.UNKNOWN;
        };
    }
}
