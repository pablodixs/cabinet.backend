package com.scriptles.cabinet.user.service;

import com.scriptles.cabinet.common.outbox.DomainOutboxPublisher;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.Review;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.repository.ExternalReferenceRepository;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.media.repository.RatingRepository;
import com.scriptles.cabinet.media.repository.ReviewLikeRepository;
import com.scriptles.cabinet.media.repository.ReviewRepository;
import com.scriptles.cabinet.media.service.MediaConsumptionPolicy;
import com.scriptles.cabinet.media.service.MediaCommunityCacheInvalidator;
import com.scriptles.cabinet.media.service.MediaLikeService;
import com.scriptles.cabinet.user.dto.request.CreateDiaryEntryRequest;
import com.scriptles.cabinet.user.dto.request.UpdateDiaryEntryRequest;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.entity.UserMediaActivity;
import com.scriptles.cabinet.user.enums.ProfileActivityType;
import com.scriptles.cabinet.user.enums.Visibility;
import com.scriptles.cabinet.user.repository.UserMediaActivityRepository;
import com.scriptles.cabinet.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiaryServiceTest {
    @Mock UserMediaActivityRepository activityRepository;
    @Mock UserRepository userRepository;
    @Mock MediaRepository mediaRepository;
    @Mock RatingRepository ratingRepository;
    @Mock ReviewRepository reviewRepository;
    @Mock ReviewLikeRepository reviewLikeRepository;
    @Mock ExternalReferenceRepository externalReferenceRepository;
    @Mock MediaConsumptionPolicy mediaConsumptionPolicy;
    @Mock MediaCommunityCacheInvalidator communityCacheInvalidator;
    @Mock MediaLikeService mediaLikeService;
    @Mock UserMediaService userMediaService;
    @Mock EpisodeTrackingService episodeTrackingService;
    @Mock UserTagService userTagService;
    @Mock UserFeedService userFeedService;
    @Mock DomainOutboxPublisher domainOutboxPublisher;
    @InjectMocks DiaryService service;

    @BeforeEach
    void assignDatabaseGeneratedReviewIds() {
        lenient().when(reviewRepository.saveAndFlush(any(Review.class))).thenAnswer(invocation -> {
            Review review = invocation.getArgument(0);
            if (review.getId() == null) review.setId(UUID.randomUUID());
            return review;
        });
    }

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
        verify(reviewRepository).saveAndFlush(reviewCaptor.capture());
        assertThat(reviewCaptor.getValue().getRatingEntity()).isNull();
        assertThat(reviewCaptor.getValue().getActivity()).isNotNull();
        verify(userMediaService).markCompleted(user, movie, false);
    }

    @Test
    void returnsFormattedReviewContentWithTheCreatedDiaryEntry() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        User user = user(userId);
        Media movie = media(mediaId, MediaType.MOVIE);
        JsonNode richContent = new ObjectMapper().readTree(
                "{\"version\":1,\"blocks\":[{\"type\":\"paragraph\",\"children\":[{\"text\":\"Uma ótima sessão.\",\"marks\":[\"bold\"]}]}]}"
        );
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
                "Uma ótima sessão.",
                false,
                Visibility.PUBLIC,
                Set.of(),
                null,
                richContent
        ));

        assertThat(response.richContent()).isEqualTo(richContent);
    }

    @Test
    void returnsStoredRichContentWhenLoadingDiaryEntries() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        User user = user(userId);
        Media movie = media(mediaId, MediaType.MOVIE);
        UserMediaActivity activity = new UserMediaActivity();
        activity.setId(entryId);
        activity.setUser(user);
        activity.setMedia(movie);
        activity.setType(ProfileActivityType.LOGGED);
        activity.setOccurredOn(LocalDate.of(2026, 7, 19));
        activity.setReviewContent("Uma ótima sessão.");
        JsonNode richContent = new ObjectMapper().readTree(
                "{\"version\":1,\"blocks\":[{\"type\":\"paragraph\",\"children\":[{\"text\":\"Uma ótima sessão.\",\"marks\":[\"bold\"]}]}]}"
        );
        Review review = new Review();
        review.setActivity(activity);
        review.setRichContent(richContent.toString());
        review.setVisibility(Visibility.PUBLIC);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(activityRepository.findDiaryEntries(any(), any(), any(), any(Boolean.class), any(), any()))
                .thenReturn(new PageImpl<>(List.of(activity)));
        when(externalReferenceRepository.findAllByMediaIdInAndPrimaryReferenceTrue(List.of(mediaId)))
                .thenReturn(List.of());
        when(reviewRepository.findDiaryRichContent(List.of(entryId), List.of(
                Visibility.PUBLIC, Visibility.FOLLOWERS, Visibility.PRIVATE
        ))).thenReturn(List.of(review));

        var response = service.findMine(userId, 0, 20);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).richContent()).isEqualTo(richContent);
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
        when(ratingRepository.save(any())).thenAnswer(invocation -> {
            com.scriptles.cabinet.media.entity.Rating rating = invocation.getArgument(0);
            if (rating.getId() == null) rating.setId(UUID.randomUUID());
            return rating;
        });
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

    @ParameterizedTest
    @EnumSource(value = MediaType.class, names = {"TRACK", "EPISODE"})
    void createsDiaryReviewForChildMedia(MediaType type) {
        UUID userId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        User user = user(userId);
        Media child = media(mediaId, type);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(mediaRepository.findById(mediaId)).thenReturn(Optional.of(child));
        when(ratingRepository.findByUserIdAndMediaId(userId, mediaId)).thenReturn(Optional.empty());
        when(reviewRepository.findByUserIdAndMediaId(userId, mediaId)).thenReturn(Optional.empty());
        when(activityRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            UserMediaActivity entry = invocation.getArgument(0);
            entry.setId(UUID.randomUUID());
            return entry;
        });

        var response = service.create(userId, new CreateDiaryEntryRequest(
                mediaId, LocalDate.of(2026, 7, 19), false, null,
                "Review da faixa ou episódio", false, Visibility.PUBLIC, Set.of()));

        assertThat(response.review()).isEqualTo("Review da faixa ou episódio");
        verify(reviewRepository).saveAndFlush(any(Review.class));
    }

    @Test
    void removingTextFromLinkedDiaryEntryDeletesItsReview() {
        UUID userId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        User user = user(userId);
        Media media = media(mediaId, MediaType.MOVIE);
        UserMediaActivity activity = new UserMediaActivity();
        activity.setId(entryId);
        activity.setType(ProfileActivityType.LOGGED);
        activity.setMedia(media);
        activity.setReviewContent("Antes");
        Review review = new Review();
        review.setId(UUID.randomUUID());
        review.setActivity(activity);
        when(activityRepository.findByIdAndUserId(entryId, userId)).thenReturn(Optional.of(activity));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(ratingRepository.findByUserIdAndMediaId(userId, mediaId)).thenReturn(Optional.empty());
        when(activityRepository.saveAndFlush(activity)).thenReturn(activity);
        when(reviewRepository.findByActivityId(entryId)).thenReturn(Optional.of(review));

        var response = service.update(userId, entryId, new UpdateDiaryEntryRequest(
                LocalDate.of(2026, 7, 19), false, null, "  ", false,
                Visibility.PUBLIC, Set.of()));

        assertThat(response.review()).isNull();
        verify(reviewLikeRepository).deleteByReviewId(review.getId());
        verify(reviewLikeRepository).flush();
        verify(reviewRepository).delete(review);
        verify(reviewRepository).flush();
    }

    @Test
    void deletingAnEntryUnlinksButPreservesItsCanonicalReview() {
        UUID userId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        UserMediaActivity activity = new UserMediaActivity();
        activity.setId(entryId);
        activity.setType(ProfileActivityType.LOGGED);
        activity.setMedia(media(UUID.randomUUID(), MediaType.MOVIE));
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
