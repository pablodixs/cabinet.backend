package com.scriptles.cabinet.user.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.SeriesDetails;
import com.scriptles.cabinet.media.entity.SeriesEpisode;
import com.scriptles.cabinet.media.entity.SeriesSeason;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.enums.SeriesStatus;
import com.scriptles.cabinet.media.repository.SeriesDetailsRepository;
import com.scriptles.cabinet.media.repository.SeriesEpisodeRepository;
import com.scriptles.cabinet.user.dto.response.LibraryEntryResponse;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.entity.UserEpisodeWatch;
import com.scriptles.cabinet.user.entity.UserMedia;
import com.scriptles.cabinet.user.enums.UserMediaStatus;
import com.scriptles.cabinet.user.repository.UserEpisodeWatchRepository;
import com.scriptles.cabinet.user.repository.UserMediaRepository;
import com.scriptles.cabinet.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EpisodeTrackingServiceTest {
    @Mock SeriesEpisodeRepository episodeRepository;
    @Mock SeriesDetailsRepository detailsRepository;
    @Mock UserEpisodeWatchRepository watchRepository;
    @Mock UserMediaRepository userMediaRepository;
    @Mock UserRepository userRepository;
    @Mock UserMediaService userMediaService;
    @InjectMocks EpisodeTrackingService service;

    @Test
    void rejectsFutureEpisodesWithoutCreatingProgress() {
        SeriesEpisode episode = episode(1, 2, LocalDate.now().plusDays(1));
        when(episodeRepository.findByEpisodeMediaId(episode.getEpisodeMedia().getId()))
                .thenReturn(Optional.of(episode));

        assertThatThrownBy(() -> service.markWatched(UUID.randomUUID(), episode.getEpisodeMedia().getId(), false))
                .isInstanceOf(ApiException.class)
                .hasMessage("O episódio ainda não foi exibido");
        verify(watchRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void marksPreviousEpisodesAndStartsAPlannedSeries() {
        UUID userId = UUID.randomUUID();
        User user = user(userId);
        SeriesEpisode first = episode(1, 1, LocalDate.now().minusDays(7));
        SeriesEpisode second = episode(1, 2, LocalDate.now());
        second.setSeason(first.getSeason());
        UserMedia planned = library(user, second.getSeason().getSeries(), UserMediaStatus.PLANNED);
        UserEpisodeWatch persisted = watch(user, second);

        when(episodeRepository.findByEpisodeMediaId(second.getEpisodeMedia().getId())).thenReturn(Optional.of(second));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(watchRepository.findPreviousUnwatched(
                userId, second.getSeason().getSeries().getId(), 1, 2, LocalDate.now()))
                .thenReturn(List.of(first));
        when(userMediaRepository.findByUserIdAndMediaId(userId, second.getSeason().getSeries().getId()))
                .thenReturn(Optional.of(planned));
        when(userMediaService.upsert(userId, second.getSeason().getSeries().getId(), UserMediaStatus.IN_PROGRESS))
                .thenReturn(libraryResponse(second.getSeason().getSeries().getId(), UserMediaStatus.IN_PROGRESS));
        when(watchRepository.findByUserIdAndEpisodeId(userId, second.getId())).thenReturn(Optional.of(persisted));

        var response = service.markWatched(userId, second.getEpisodeMedia().getId(), true);

        ArgumentCaptor<List<UserEpisodeWatch>> watches = ArgumentCaptor.forClass(List.class);
        verify(watchRepository).saveAllAndFlush(watches.capture());
        assertThat(watches.getValue()).extracting(watch -> watch.getEpisode().getEpisodeNumber())
                .containsExactly(1, 2);
        assertThat(response.changedEpisodeIds()).containsExactly(
                first.getEpisodeMedia().getId(), second.getEpisodeMedia().getId());
        assertThat(response.seriesStatus()).isEqualTo(UserMediaStatus.IN_PROGRESS);
    }

    @Test
    void completesAnEndedSeriesAfterItsLastRegularEpisode() {
        UUID userId = UUID.randomUUID();
        User user = user(userId);
        SeriesEpisode episode = episode(2, 8, LocalDate.now());
        UserMedia inProgress = library(user, episode.getSeason().getSeries(), UserMediaStatus.IN_PROGRESS);
        SeriesDetails details = new SeriesDetails();
        details.setStatus(SeriesStatus.ENDED);

        when(episodeRepository.findByEpisodeMediaId(episode.getEpisodeMedia().getId()))
                .thenReturn(Optional.of(episode));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userMediaRepository.findByUserIdAndMediaId(userId, episode.getSeason().getSeries().getId()))
                .thenReturn(Optional.of(inProgress));
        when(watchRepository.findByUserIdAndEpisodeId(userId, episode.getId()))
                .thenReturn(Optional.of(watch(user, episode)));
        when(detailsRepository.findById(episode.getSeason().getSeries().getId())).thenReturn(Optional.of(details));
        when(episodeRepository.countBySeasonSeriesIdAndSeasonSeasonNumberGreaterThan(
                episode.getSeason().getSeries().getId(), 0)).thenReturn(8L);
        when(watchRepository.countRegularWatched(userId, episode.getSeason().getSeries().getId())).thenReturn(8L);
        when(userMediaService.upsert(userId, episode.getSeason().getSeries().getId(), UserMediaStatus.COMPLETED))
                .thenReturn(libraryResponse(episode.getSeason().getSeries().getId(), UserMediaStatus.COMPLETED));

        var response = service.markWatched(userId, episode.getEpisodeMedia().getId(), false);

        assertThat(response.seriesStatus()).isEqualTo(UserMediaStatus.COMPLETED);
        verify(userMediaService).upsert(userId, episode.getSeason().getSeries().getId(), UserMediaStatus.COMPLETED);
    }

    private static SeriesEpisode episode(int seasonNumber, int episodeNumber, LocalDate airDate) {
        Media series = new Media();
        series.setId(UUID.randomUUID());
        series.setType(MediaType.SERIES);
        series.setTitle("Série");
        SeriesSeason season = new SeriesSeason();
        season.setId(UUID.randomUUID());
        season.setSeries(series);
        season.setSeasonNumber(seasonNumber);
        Media episodeMedia = new Media();
        episodeMedia.setId(UUID.randomUUID());
        episodeMedia.setType(MediaType.EPISODE);
        episodeMedia.setTitle("Episódio");
        SeriesEpisode episode = new SeriesEpisode();
        episode.setId(UUID.randomUUID());
        episode.setSeason(season);
        episode.setEpisodeMedia(episodeMedia);
        episode.setEpisodeNumber(episodeNumber);
        episode.setTitle("Episódio " + episodeNumber);
        episode.setAirDate(airDate);
        return episode;
    }

    private static User user(UUID id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private static UserMedia library(User user, Media series, UserMediaStatus status) {
        UserMedia entry = new UserMedia();
        entry.setUser(user);
        entry.setMedia(series);
        entry.setStatus(status);
        return entry;
    }

    private static UserEpisodeWatch watch(User user, SeriesEpisode episode) {
        UserEpisodeWatch watch = new UserEpisodeWatch();
        watch.setUser(user);
        watch.setEpisode(episode);
        watch.setWatchedAt(Instant.now());
        return watch;
    }

    private static LibraryEntryResponse libraryResponse(UUID seriesId, UserMediaStatus status) {
        return new LibraryEntryResponse(UUID.randomUUID(), seriesId, status, null, null, null, null, null);
    }
}
