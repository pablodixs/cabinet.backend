package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.common.time.CabinetTime;
import com.scriptles.cabinet.media.dto.response.SeasonEpisodesResponse;
import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.SeriesEpisode;
import com.scriptles.cabinet.media.entity.SeriesSeason;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.external.ExternalMedia;
import com.scriptles.cabinet.media.external.TmdbClient;
import com.scriptles.cabinet.media.repository.ExternalReferenceRepository;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.media.repository.SeriesEpisodeRepository;
import com.scriptles.cabinet.media.repository.SeriesSeasonRepository;
import com.scriptles.cabinet.user.repository.UserEpisodeWatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Instant;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SeasonEpisodeService {
    private final MediaRepository mediaRepository;
    private final SeriesSeasonRepository seasonRepository;
    private final SeriesEpisodeRepository episodeRepository;
    private final ExternalReferenceRepository referenceRepository;
    private final TmdbClient tmdbClient;
    private final RatingSummaryService ratingSummaryService;
    private final UserEpisodeWatchRepository watchRepository;

    @Transactional
    public SeasonEpisodesResponse find(UUID seriesId, int seasonNumber, String language, UUID userId) {
        Media series = mediaRepository.findById(seriesId).orElseThrow(() -> notFound("MEDIA_NOT_FOUND", "Mídia não encontrada"));
        if (series.getType() != MediaType.SERIES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SERIES", "A mídia informada não é uma série");
        }
        SeriesSeason season = seasonRepository.findLockedBySeriesIdAndSeasonNumber(seriesId, seasonNumber)
                .orElseThrow(() -> notFound("SEASON_NOT_FOUND", "Temporada não encontrada"));
        ExternalReference tmdb = referenceRepository.findByMediaIdAndSource(seriesId, ExternalSource.TMDB)
                .orElseThrow(() -> notFound("TMDB_REFERENCE_NOT_FOUND", "Série sem referência do TMDB"));

        sync(season, tmdb.getExternalId(), language);
        List<SeriesEpisode> episodes = episodeRepository.findAllBySeasonIdOrderByEpisodeNumberAsc(season.getId());
        List<UUID> ids = episodes.stream().map(e -> e.getEpisodeMedia().getId()).toList();
        Map<UUID, RatingSummaryService.ItemStats> itemStats = ratingSummaryService.items(ids, userId);
        RatingSummaryService.SeasonStats seasonStats = ratingSummaryService.season(episodes, userId);
        Set<UUID> watchedIds = userId == null || episodes.isEmpty()
                ? Set.of()
                : watchRepository.findWatchedEpisodeIds(
                        userId, episodes.stream().map(SeriesEpisode::getId).toList());

        return new SeasonEpisodesResponse(seriesId, tmdb.getExternalId(), season.getId(), seasonNumber,
                seasonStats.averageRating(), seasonStats.ratingCount(), seasonStats.myRating(),
                seasonStats.myRatedEpisodeCount(), seasonStats.eligibleEpisodeCount(),
                episodes.stream().map(episode -> {
                    RatingSummaryService.ItemStats stats = itemStats.getOrDefault(
                            episode.getEpisodeMedia().getId(), RatingSummaryService.ItemStats.empty());
                    return new SeasonEpisodesResponse.EpisodeResponse(
                            episode.getEpisodeMedia().getId(), episode.getExternalId(), episode.getEpisodeNumber(),
                            episode.getTitle(), episode.getDescription(), episode.getStillUrl(), episode.getAirDate(),
                            episode.getRuntimeMinutes(), stats.averageRating(), stats.ratingCount(), stats.myRating(),
                            ratingSummaryService.eligible(episode), watchedIds.contains(episode.getId()),
                            previousUnwatchedCount(userId, episode));
                }).toList());
    }

    @Transactional
    public void syncSeason(UUID seriesId, int seasonNumber, String seriesExternalId, String language) {
        SeriesSeason season = seasonRepository.findLockedBySeriesIdAndSeasonNumber(seriesId, seasonNumber)
                .orElseThrow(() -> notFound("SEASON_NOT_FOUND", "Temporada não encontrada"));
        sync(season, seriesExternalId, language);
    }

    private void sync(SeriesSeason season, String seriesExternalId, String language) {
        for (ExternalMedia.ExternalEpisode external : tmdbClient.findSeasonEpisodes(
                seriesExternalId, season.getSeasonNumber(), language)) {
            SeriesEpisode episode = episodeRepository
                    .findBySeasonIdAndEpisodeNumber(season.getId(), external.episodeNumber())
                    .orElseGet(() -> createEpisode(season, external));
            episode.setExternalId(external.externalId());
            episode.setTitle(external.title());
            episode.setDescription(external.description());
            episode.setStillUrl(external.stillUrl());
            episode.setAirDate(external.airDate());
            episode.setRuntimeMinutes(external.runtimeMinutes());
            Media media = episode.getEpisodeMedia();
            media.setTitle(external.title());
            media.setDescription(external.description());
            media.setCoverUrl(external.stillUrl());
            media.setReleaseDate(external.airDate());
            mediaRepository.save(media);
            episodeRepository.save(episode);
        }
        season.setEpisodesSyncedAt(Instant.now());
        seasonRepository.save(season);
    }

    private long previousUnwatchedCount(UUID userId, SeriesEpisode episode) {
        if (userId == null || episode.getSeason().getSeasonNumber() <= 0) return 0;
        return watchRepository.countPreviousUnwatched(
                userId,
                episode.getSeason().getSeries().getId(),
                episode.getSeason().getSeasonNumber(),
                episode.getEpisodeNumber(),
                CabinetTime.today()
        );
    }

    private SeriesEpisode createEpisode(SeriesSeason season, ExternalMedia.ExternalEpisode external) {
        Media media = new Media();
        media.setType(MediaType.EPISODE);
        media.setTitle(external.title());
        media.setDescription(external.description());
        media.setCoverUrl(external.stillUrl());
        media.setReleaseDate(external.airDate());
        Media savedMedia = mediaRepository.save(media);

        SeriesEpisode episode = new SeriesEpisode();
        episode.setSeason(season);
        episode.setEpisodeMedia(savedMedia);
        episode.setEpisodeNumber(external.episodeNumber());
        episode.setTitle(external.title());
        return episode;
    }

    private ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }
}
