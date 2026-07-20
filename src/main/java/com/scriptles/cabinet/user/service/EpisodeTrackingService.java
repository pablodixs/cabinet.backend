package com.scriptles.cabinet.user.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.common.time.CabinetTime;
import com.scriptles.cabinet.media.entity.SeriesDetails;
import com.scriptles.cabinet.media.entity.SeriesEpisode;
import com.scriptles.cabinet.media.enums.SeriesStatus;
import com.scriptles.cabinet.media.repository.SeriesDetailsRepository;
import com.scriptles.cabinet.media.repository.SeriesEpisodeRepository;
import com.scriptles.cabinet.user.dto.response.EpisodeWatchResponse;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.entity.UserEpisodeWatch;
import com.scriptles.cabinet.user.entity.UserMedia;
import com.scriptles.cabinet.user.enums.UserMediaStatus;
import com.scriptles.cabinet.user.repository.UserEpisodeWatchRepository;
import com.scriptles.cabinet.user.repository.UserMediaRepository;
import com.scriptles.cabinet.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EpisodeTrackingService {
    private final SeriesEpisodeRepository episodeRepository;
    private final SeriesDetailsRepository seriesDetailsRepository;
    private final UserEpisodeWatchRepository watchRepository;
    private final UserMediaRepository userMediaRepository;
    private final UserRepository userRepository;
    private final UserMediaService userMediaService;

    @Transactional
    public EpisodeWatchResponse markWatched(UUID userId, UUID episodeMediaId, boolean includePrevious) {
        SeriesEpisode episode = findEpisode(episodeMediaId);
        LocalDate today = CabinetTime.today();
        if (episode.getAirDate() != null && episode.getAirDate().isAfter(today)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EPISODE_NOT_RELEASED",
                    "O episódio ainda não foi exibido");
        }

        User user = userRepository.findById(userId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Usuário não encontrado"));
        List<SeriesEpisode> targets = new ArrayList<>();
        if (includePrevious && episode.getSeason().getSeasonNumber() > 0) {
            targets.addAll(watchRepository.findPreviousUnwatched(
                    userId,
                    episode.getSeason().getSeries().getId(),
                    episode.getSeason().getSeasonNumber(),
                    episode.getEpisodeNumber(),
                    today
            ));
        }
        if (!watchRepository.existsByUserIdAndEpisodeId(userId, episode.getId())) {
            targets.add(episode);
        }

        Instant watchedAt = Instant.now();
        List<UserEpisodeWatch> watches = targets.stream().map(target -> {
            UserEpisodeWatch watch = new UserEpisodeWatch();
            watch.setUser(user);
            watch.setEpisode(target);
            watch.setWatchedAt(watchedAt);
            return watch;
        }).toList();
        if (!watches.isEmpty()) {
            watchRepository.saveAllAndFlush(watches);
        }

        UUID seriesId = episode.getSeason().getSeries().getId();
        UserMediaStatus status = startSeriesIfNeeded(userId, seriesId);
        status = completeEndedSeriesIfEligible(userId, seriesId, status);
        Instant effectiveWatchedAt = watchRepository.findByUserIdAndEpisodeId(userId, episode.getId())
                .map(UserEpisodeWatch::getWatchedAt)
                .orElse(watchedAt);
        return new EpisodeWatchResponse(
                episodeMediaId,
                true,
                effectiveWatchedAt,
                targets.stream().map(target -> target.getEpisodeMedia().getId()).toList(),
                status
        );
    }

    @Transactional
    public EpisodeWatchResponse unmarkWatched(UUID userId, UUID episodeMediaId) {
        SeriesEpisode episode = findEpisode(episodeMediaId);
        watchRepository.findByUserIdAndEpisodeId(userId, episode.getId()).ifPresent(watchRepository::delete);
        UserMedia entry = userMediaRepository.findByUserIdAndMediaId(
                userId, episode.getSeason().getSeries().getId()).orElse(null);
        UserMediaStatus status = entry == null ? null : entry.getStatus();
        if (status == UserMediaStatus.COMPLETED) {
            status = userMediaService.upsert(
                    userId, episode.getSeason().getSeries().getId(), UserMediaStatus.IN_PROGRESS).status();
        }
        return new EpisodeWatchResponse(episodeMediaId, false, null, List.of(episodeMediaId), status);
    }

    private UserMediaStatus startSeriesIfNeeded(UUID userId, UUID seriesId) {
        UserMedia entry = userMediaRepository.findByUserIdAndMediaId(userId, seriesId).orElse(null);
        if (entry == null || entry.getStatus() == UserMediaStatus.PLANNED) {
            return userMediaService.upsert(userId, seriesId, UserMediaStatus.IN_PROGRESS).status();
        }
        return entry.getStatus();
    }

    private UserMediaStatus completeEndedSeriesIfEligible(
            UUID userId,
            UUID seriesId,
            UserMediaStatus currentStatus
    ) {
        if (currentStatus != UserMediaStatus.IN_PROGRESS) {
            return currentStatus;
        }
        SeriesDetails details = seriesDetailsRepository.findById(seriesId).orElse(null);
        if (details == null || (details.getStatus() != SeriesStatus.ENDED
                && details.getStatus() != SeriesStatus.CANCELLED)) {
            return currentStatus;
        }
        long episodeCount = episodeRepository
                .countBySeasonSeriesIdAndSeasonSeasonNumberGreaterThan(seriesId, 0);
        if (episodeCount > 0 && watchRepository.countRegularWatched(userId, seriesId) == episodeCount) {
            return userMediaService.upsert(userId, seriesId, UserMediaStatus.COMPLETED).status();
        }
        return currentStatus;
    }

    private SeriesEpisode findEpisode(UUID episodeMediaId) {
        return episodeRepository.findByEpisodeMediaId(episodeMediaId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "EPISODE_NOT_FOUND", "Episódio não encontrado"));
    }
}
