package com.scriptles.cabinet.user.service;

import com.scriptles.cabinet.media.entity.SeriesEpisode;
import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.SeriesSeason;
import com.scriptles.cabinet.media.service.SeriesTrackingSyncScheduler;
import com.scriptles.cabinet.media.service.UserArtworkResolver;
import com.scriptles.cabinet.media.repository.SeriesSeasonRepository;
import com.scriptles.cabinet.media.repository.ExternalReferenceRepository;
import com.scriptles.cabinet.common.time.CabinetTime;
import com.scriptles.cabinet.user.dto.response.EpisodeAgendaResponse;
import com.scriptles.cabinet.user.enums.UserMediaStatus;
import com.scriptles.cabinet.user.repository.UserEpisodeWatchRepository;
import com.scriptles.cabinet.user.repository.UserMediaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class EpisodeAgendaService {
    private static final Duration STALE_AFTER = Duration.ofHours(24);

    private final UserEpisodeWatchRepository watchRepository;
    private final UserMediaRepository userMediaRepository;
    private final SeriesTrackingSyncScheduler syncScheduler;
    private final SeriesSeasonRepository seasonRepository;
    private final ExternalReferenceRepository referenceRepository;
    private final UserArtworkResolver userArtworkResolver;

    @Transactional(readOnly = true)
    public EpisodeAgendaResponse find(UUID userId, int days, int overdueLimit) {
        List<UUID> trackedSeriesIds = userMediaRepository.findSeriesIdsForUserAndStatus(
                userId, UserMediaStatus.IN_PROGRESS);
        trackedSeriesIds.forEach(syncScheduler::scheduleIfStale);

        LocalDate today = CabinetTime.today();
        List<SeriesEpisode> overdue = watchRepository.findOverdue(
                userId, today, PageRequest.of(0, overdueLimit));
        List<SeriesEpisode> upcoming = watchRepository.findUpcoming(
                userId, today, today.plusDays(days));
        Set<UUID> watchedIds = watchedIds(userId, upcoming);
        List<Media> series = Stream.concat(overdue.stream(), upcoming.stream())
                .map(episode -> episode.getSeason().getSeries())
                .distinct()
                .toList();
        Map<UUID, UserArtworkResolver.ResolvedArtwork> artworks =
                userArtworkResolver.resolve(userId, series);
        SyncState syncState = syncState(trackedSeriesIds);
        return new EpisodeAgendaResponse(
                syncState.pending(),
                syncState.lastSyncedAt(),
                watchRepository.countOverdue(userId, today),
                overdue.stream().map(episode -> item(episode, false, artworks)).toList(),
                upcoming.stream().map(episode -> item(
                        episode, watchedIds.contains(episode.getId()), artworks)).toList()
        );
    }

    private Set<UUID> watchedIds(UUID userId, List<SeriesEpisode> episodes) {
        if (episodes.isEmpty()) return Set.of();
        return watchRepository.findWatchedEpisodeIds(
                userId, episodes.stream().map(SeriesEpisode::getId).toList());
    }

    private SyncState syncState(List<UUID> trackedSeriesIds) {
        if (trackedSeriesIds.isEmpty()) return new SyncState(false, null);
        List<ExternalReference> references = referenceRepository
                .findAllByMediaIdInAndPrimaryReferenceTrue(trackedSeriesIds);
        List<SeriesSeason> seasons = seasonRepository
                .findAllBySeriesIdInAndSeasonNumberGreaterThanOrderBySeriesIdAscSeasonNumberAsc(
                        trackedSeriesIds, 0);
        Instant oldest = null;
        for (ExternalReference reference : references) {
            Instant syncedAt = reference.getLastSyncedAt();
            if (syncedAt != null && (oldest == null || syncedAt.isBefore(oldest))) oldest = syncedAt;
        }
        boolean pending = references.size() < trackedSeriesIds.size()
                || seasons.stream().anyMatch(season -> season.getEpisodesSyncedAt() == null)
                || oldest == null
                || oldest.isBefore(Instant.now().minus(STALE_AFTER));
        return new SyncState(pending, oldest);
    }

    private EpisodeAgendaResponse.Item item(
            SeriesEpisode episode,
            boolean watched,
            Map<UUID, UserArtworkResolver.ResolvedArtwork> artworks
    ) {
        SeriesSeason season = episode.getSeason();
        Media series = season.getSeries();
        UserArtworkResolver.ResolvedArtwork artwork = artworks.get(series.getId());
        return new EpisodeAgendaResponse.Item(
                episode.getEpisodeMedia().getId(),
                series.getId(),
                series.getTitle(),
                artwork == null ? series.getCoverUrl() : artwork.coverUrl(),
                season.getSeasonNumber(),
                episode.getEpisodeNumber(),
                episode.getTitle(),
                episode.getStillUrl(),
                episode.getAirDate(),
                watched
        );
    }

    private record SyncState(boolean pending, Instant lastSyncedAt) {
    }
}
