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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RatingSummaryServiceTest {
    @Mock RatingRepository ratingRepository;
    @InjectMocks RatingSummaryService service;

    @Test
    void aggregateWeightsEveryPublicChildRatingAndBuildsAllBuckets() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        List<UUID> ids = List.of(first, second);
        RatingRepository.AggregateRatingProjection summary =
                mock(RatingRepository.AggregateRatingProjection.class);
        RatingRepository.RatingDistributionProjection threeStars =
                mock(RatingRepository.RatingDistributionProjection.class);
        RatingRepository.RatingDistributionProjection fiveStars =
                mock(RatingRepository.RatingDistributionProjection.class);
        when(summary.getAverageRating()).thenReturn(13.0 / 3.0);
        when(summary.getRatingCount()).thenReturn(3L);
        when(threeStars.getRating()).thenReturn(new BigDecimal("3.0"));
        when(threeStars.getRatingCount()).thenReturn(1L);
        when(fiveStars.getRating()).thenReturn(new BigDecimal("5.0"));
        when(fiveStars.getRatingCount()).thenReturn(2L);
        when(ratingRepository.aggregateRatings(ids, Visibility.PUBLIC)).thenReturn(summary);
        when(ratingRepository.ratingDistributionForMediaIds(ids, Visibility.PUBLIC))
                .thenReturn(List.of(threeStars, fiveStars));

        var result = service.aggregate(ids);

        assertThat(result.averageRating()).isEqualTo(4.33);
        assertThat(result.ratingCount()).isEqualTo(3);
        assertThat(result.ratingDistribution()).hasSize(10);
        assertThat(result.ratingDistribution().get(5).count()).isEqualTo(1);
        assertThat(result.ratingDistribution().get(9).count()).isEqualTo(2);
    }

    @Test
    void aggregateReturnsTheAverageForOneRatedWork() {
        UUID mediaId = UUID.randomUUID();
        RatingRepository.AggregateRatingProjection summary =
                mock(RatingRepository.AggregateRatingProjection.class);
        RatingRepository.RatingDistributionProjection ratingBucket =
                mock(RatingRepository.RatingDistributionProjection.class);
        when(summary.getAverageRating()).thenReturn(4.5);
        when(summary.getRatingCount()).thenReturn(1L);
        when(ratingBucket.getRating()).thenReturn(new BigDecimal("4.5"));
        when(ratingBucket.getRatingCount()).thenReturn(1L);
        when(ratingRepository.aggregateRatings(List.of(mediaId), Visibility.PUBLIC)).thenReturn(summary);
        when(ratingRepository.ratingDistributionForMediaIds(List.of(mediaId), Visibility.PUBLIC))
                .thenReturn(List.of(ratingBucket));

        var result = service.aggregate(List.of(mediaId));

        assertThat(result.averageRating()).isEqualTo(4.5);
        assertThat(result.ratingCount()).isEqualTo(1);
    }

    @Test
    void emptyAggregateHasNoAverageAndTenEmptyBuckets() {
        var result = service.aggregate(List.of());

        assertThat(result.averageRating()).isNull();
        assertThat(result.ratingCount()).isZero();
        assertThat(result.ratingDistribution()).hasSize(10)
                .allMatch(bucket -> bucket.count() == 0);
    }

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
