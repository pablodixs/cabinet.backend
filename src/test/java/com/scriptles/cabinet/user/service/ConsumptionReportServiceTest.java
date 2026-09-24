package com.scriptles.cabinet.user.service;

import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.CreditRole;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.repository.AlbumTrackRepository;
import com.scriptles.cabinet.media.repository.MediaCreditRepository;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.media.repository.ReportRankingProjection;
import com.scriptles.cabinet.media.repository.SeriesEpisodeRepository;
import com.scriptles.cabinet.user.dto.response.ConsumptionReportResponse;
import com.scriptles.cabinet.user.entity.UserMediaActivity;
import com.scriptles.cabinet.user.enums.ConsumptionReportPeriod;
import com.scriptles.cabinet.user.enums.ProfileActivityType;
import com.scriptles.cabinet.user.repository.ConsumptionReportActivityRepository;
import com.scriptles.cabinet.user.repository.UserEpisodeWatchRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsumptionReportServiceTest {
    @Mock ConsumptionReportActivityRepository activityRepository;
    @Mock UserEpisodeWatchRepository episodeWatchRepository;
    @Mock SeriesEpisodeRepository seriesEpisodeRepository;
    @Mock AlbumTrackRepository albumTrackRepository;
    @Mock MediaRepository mediaRepository;
    @Mock MediaCreditRepository mediaCreditRepository;

    @Test
    void aggregatesMonthlyEventsAndKeepsTopFiveStable() {
        Media movie = media(MediaType.MOVIE, "Movie", LocalDate.of(2024, 1, 2));
        movie.getGenres().add("Drama");
        movie.setCountryCode("BR");
        movie.setOriginalLanguage("pt");
        UUID directorId = UUID.randomUUID();
        UserMediaActivity first = activity(movie, LocalDate.of(2024, 1, 3), ProfileActivityType.COMPLETED);
        UserMediaActivity second = activity(movie, LocalDate.of(2024, 1, 4), ProfileActivityType.REWATCHED);

        when(activityRepository.findAllConsumptionActivities(any(), anyCollection()))
                .thenReturn(List.of(first, second));
        when(episodeWatchRepository.findAllConsumptionWatches(any())).thenReturn(List.of());
        when(mediaRepository.findAllWithGenresByIdIn(anyCollection())).thenReturn(List.of(movie));
        ReportRankingProjection ranking = org.mockito.Mockito.mock(ReportRankingProjection.class);
        when(ranking.getPersonId()).thenReturn(directorId);
        when(ranking.getPersonName()).thenReturn("Walter Salles");
        when(ranking.getPersonImageUrl()).thenReturn(null);
        when(ranking.getEventCount()).thenReturn(2L);
        when(ranking.getEligibleEventCount()).thenReturn(2L);
        when(ranking.getAttributedEventCount()).thenReturn(2L);
        when(mediaCreditRepository.rankReportPeople(any(), org.mockito.ArgumentMatchers.eq(CreditRole.DIRECTOR.name()),
                org.mockito.ArgumentMatchers.eq(5))).thenReturn(List.of(ranking));
        ConsumptionReportResponse response = service().find(
                UUID.randomUUID(), ConsumptionReportPeriod.MONTH, 2024, 1, MediaType.MOVIE);

        assertThat(response.totalConsumptions()).isEqualTo(2);
        assertThat(response.topDirectors().items()).singleElement()
                .satisfies(item -> {
                    assertThat(item.label()).isEqualTo("Walter Salles");
                    assertThat(item.count()).isEqualTo(2);
                });
        assertThat(response.topGenres().items()).singleElement()
                .extracting(item -> item.count()).isEqualTo(2L);
        assertThat(response.topReleaseYear().items()).singleElement()
                .extracting(item -> item.label()).isEqualTo("2024");
        assertThat(response.availablePeriods()).containsExactly(
                new com.scriptles.cabinet.user.dto.response.ConsumptionReportPeriodOption(2024, 1));
    }

    @Test
    void rejectsFuturePeriodsBeforeQuerying() {
        assertThatThrownBy(() -> service().find(
                UUID.randomUUID(), ConsumptionReportPeriod.YEAR, LocalDate.now().getYear() + 1,
                null, null))
                .isInstanceOf(com.scriptles.cabinet.common.api.ApiException.class)
                .hasMessageContaining("futuro");
    }

    private ConsumptionReportService service() {
        return new ConsumptionReportService(activityRepository, episodeWatchRepository,
                seriesEpisodeRepository, albumTrackRepository, mediaRepository, mediaCreditRepository);
    }

    private Media media(MediaType type, String title, LocalDate releaseDate) {
        Media media = new Media();
        media.setId(UUID.randomUUID());
        media.setType(type);
        media.setTitle(title);
        media.setReleaseDate(releaseDate);
        return media;
    }

    private UserMediaActivity activity(Media media, LocalDate date, ProfileActivityType type) {
        UserMediaActivity activity = new UserMediaActivity();
        activity.setMedia(media);
        activity.setOccurredOn(date);
        activity.setType(type);
        return activity;
    }

}
