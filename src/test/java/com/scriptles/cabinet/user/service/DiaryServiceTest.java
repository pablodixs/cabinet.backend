package com.scriptles.cabinet.user.service;

import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.Review;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.repository.ExternalReferenceRepository;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.media.repository.RatingRepository;
import com.scriptles.cabinet.media.repository.ReviewRepository;
import com.scriptles.cabinet.media.service.MediaConsumptionPolicy;
import com.scriptles.cabinet.user.dto.request.CreateDiaryEntryRequest;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.entity.UserMediaActivity;
import com.scriptles.cabinet.user.enums.ProfileActivityType;
import com.scriptles.cabinet.user.enums.Visibility;
import com.scriptles.cabinet.user.repository.UserMediaActivityRepository;
import com.scriptles.cabinet.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiaryServiceTest {
    @Mock UserMediaActivityRepository activityRepository;
    @Mock UserRepository userRepository;
    @Mock MediaRepository mediaRepository;
    @Mock RatingRepository ratingRepository;
    @Mock ReviewRepository reviewRepository;
    @Mock ExternalReferenceRepository externalReferenceRepository;
    @Mock MediaConsumptionPolicy mediaConsumptionPolicy;
    @Mock UserMediaService userMediaService;
    @Mock EpisodeTrackingService episodeTrackingService;
    @Mock UserTagService userTagService;
    @Mock UserFeedService userFeedService;
    @InjectMocks DiaryService service;

    @Test
    void createsAHistoricalEntryAndCanonicalReviewWithoutRequiringARating() {
        UUID userId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        User user = user(userId);
        Media movie = media(mediaId, MediaType.MOVIE);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(mediaRepository.findById(mediaId)).thenReturn(Optional.of(movie));
        when(ratingRepository.findByUserIdAndMediaId(userId, mediaId)).thenReturn(Optional.empty());
        when(reviewRepository.findByUserIdAndMediaId(userId, mediaId)).thenReturn(Optional.empty());
        when(activityRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            UserMediaActivity activity = invocation.getArgument(0);
            activity.setId(UUID.randomUUID());
            return activity;
        });
        when(externalReferenceRepository.findAllByMediaIdInAndPrimaryReferenceTrue(List.of(mediaId)))
                .thenReturn(List.of());

        var response = service.create(userId, new CreateDiaryEntryRequest(
                mediaId,
                LocalDate.of(2026, 7, 19),
                false,
                null,
                "  Uma ótima sessão.  ",
                true,
                Visibility.PUBLIC,
                Set.of("cinema", "com:amigos")
        ));

        assertThat(response.type()).isEqualTo(ProfileActivityType.LOGGED);
        assertThat(response.rating()).isNull();
        assertThat(response.review()).isEqualTo("Uma ótima sessão.");
        assertThat(response.containsSpoilers()).isTrue();
        assertThat(response.tags()).containsExactlyInAnyOrder("cinema", "com:amigos");

        ArgumentCaptor<Review> reviewCaptor = ArgumentCaptor.forClass(Review.class);
        verify(reviewRepository).save(reviewCaptor.capture());
        assertThat(reviewCaptor.getValue().getRatingEntity()).isNull();
        assertThat(reviewCaptor.getValue().getActivity()).isNotNull();
        verify(userMediaService).markCompleted(user, movie, false);
    }

    @Test
    void storesAReconsumptionAsANewEntryWhileUpdatingTheCanonicalRating() {
        UUID userId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        User user = user(userId);
        Media book = media(mediaId, MediaType.BOOK);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(mediaRepository.findById(mediaId)).thenReturn(Optional.of(book));
        when(ratingRepository.findByUserIdAndMediaId(userId, mediaId)).thenReturn(Optional.empty());
        when(ratingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(activityRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            UserMediaActivity activity = invocation.getArgument(0);
            activity.setId(UUID.randomUUID());
            return activity;
        });
        when(externalReferenceRepository.findAllByMediaIdInAndPrimaryReferenceTrue(List.of(mediaId)))
                .thenReturn(List.of());

        var response = service.create(userId, new CreateDiaryEntryRequest(
                mediaId,
                LocalDate.of(2026, 7, 18),
                true,
                new BigDecimal("4.5"),
                null,
                false,
                Visibility.PRIVATE,
                Set.of()
        ));

        assertThat(response.type()).isEqualTo(ProfileActivityType.RELOGGED);
        assertThat(response.rating()).isEqualByComparingTo("4.5");
        verify(ratingRepository).save(any());
        verify(userMediaService).markCompleted(user, book, false);
    }

    @Test
    void deletingAnEntryUnlinksButPreservesItsCanonicalReview() {
        UUID userId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        UserMediaActivity activity = new UserMediaActivity();
        activity.setId(entryId);
        activity.setType(ProfileActivityType.LOGGED);
        Review review = new Review();
        review.setActivity(activity);
        when(activityRepository.findByIdAndUserId(entryId, userId)).thenReturn(Optional.of(activity));
        when(reviewRepository.findByActivityId(entryId)).thenReturn(Optional.of(review));

        service.delete(userId, entryId);

        assertThat(review.getActivity()).isNull();
        verify(reviewRepository).saveAndFlush(review);
        verify(activityRepository).delete(activity);
    }

    private User user(UUID id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private Media media(UUID id, MediaType type) {
        Media media = new Media();
        media.setId(id);
        media.setType(type);
        return media;
    }
}
