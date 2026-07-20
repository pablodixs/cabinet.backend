package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.Rating;
import com.scriptles.cabinet.media.entity.SeriesEpisode;
import com.scriptles.cabinet.media.repository.RatingRepository;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.enums.Visibility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RatingSummaryServiceTest {
    @Mock RatingRepository ratingRepository;
    @InjectMocks RatingSummaryService service;

    @Test
    void seasonGivesEveryUserEqualWeightAndReportsPersonalCoverage() {
        UUID currentUserId = UUID.randomUUID();
        User currentUser = user(currentUserId);
        User otherUser = user(UUID.randomUUID());
        SeriesEpisode first = episode(null);
        SeriesEpisode second = episode(LocalDate.now());
        SeriesEpisode future = episode(LocalDate.now().plusDays(1));
        List<UUID> eligibleIds = List.of(first.getEpisodeMedia().getId(), second.getEpisodeMedia().getId());

        // Current user average: 3.0. Other user average: 5.0. Community average: 4.0.
        when(ratingRepository.findAllByMediaIdInAndVisibility(eligibleIds, Visibility.PUBLIC))
                .thenReturn(List.of(
                        rating(currentUser, first, "2.0", Visibility.PUBLIC),
                        rating(currentUser, second, "4.0", Visibility.PUBLIC),
                        rating(otherUser, first, "5.0", Visibility.PUBLIC)));
        when(ratingRepository.findAllByUserIdAndMediaIdIn(currentUserId, eligibleIds))
                .thenReturn(List.of(
                        rating(currentUser, first, "2.0", Visibility.PUBLIC),
                        rating(currentUser, second, "4.0", Visibility.PRIVATE)));

        var result = service.season(List.of(first, second, future), currentUserId);

        assertThat(result.averageRating()).isEqualTo(4.0);
        assertThat(result.ratingCount()).isEqualTo(2);
        assertThat(result.myRating()).isEqualTo(3.0);
        assertThat(result.myRatedEpisodeCount()).isEqualTo(2);
        assertThat(result.eligibleEpisodeCount()).isEqualTo(2);
    }

    @Test
    void unratedEpisodesDoNotReducePersonalAverage() {
        UUID userId = UUID.randomUUID();
        User user = user(userId);
        SeriesEpisode first = episode(null);
        SeriesEpisode second = episode(null);
        List<UUID> ids = List.of(first.getEpisodeMedia().getId(), second.getEpisodeMedia().getId());
        when(ratingRepository.findAllByMediaIdInAndVisibility(ids, Visibility.PUBLIC)).thenReturn(List.of());
        when(ratingRepository.findAllByUserIdAndMediaIdIn(userId, ids))
                .thenReturn(List.of(rating(user, first, "4.5", Visibility.PUBLIC)));

        var result = service.season(List.of(first, second), userId);

        assertThat(result.myRating()).isEqualTo(4.5);
        assertThat(result.myRatedEpisodeCount()).isEqualTo(1);
        assertThat(result.eligibleEpisodeCount()).isEqualTo(2);
    }

    private static SeriesEpisode episode(LocalDate airDate) {
        Media media = new Media();
        media.setId(UUID.randomUUID());
        SeriesEpisode episode = new SeriesEpisode();
        episode.setEpisodeMedia(media);
        episode.setAirDate(airDate);
        return episode;
    }

    private static User user(UUID id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private static Rating rating(User user, SeriesEpisode episode, String value, Visibility visibility) {
        Rating rating = new Rating();
        rating.setUser(user);
        rating.setMedia(episode.getEpisodeMedia());
        rating.setValue(new BigDecimal(value));
        rating.setVisibility(visibility);
        return rating;
    }
}
