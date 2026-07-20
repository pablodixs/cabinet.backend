package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.media.dto.request.UpsertReviewRequest;
import com.scriptles.cabinet.media.dto.response.ReviewResponse;
import com.scriptles.cabinet.media.dto.response.MediaSearchItemResponse;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.Rating;
import com.scriptles.cabinet.media.entity.Review;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.media.repository.RatingRepository;
import com.scriptles.cabinet.media.repository.ReviewLikeRepository;
import com.scriptles.cabinet.media.repository.ReviewRepository;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.enums.Visibility;
import com.scriptles.cabinet.user.repository.UserMediaActivityRepository;
import com.scriptles.cabinet.user.repository.UserRepository;
import com.scriptles.cabinet.user.service.UserMediaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {
    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private ReviewLikeRepository reviewLikeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private MediaRepository mediaRepository;
    @Mock
    private RatingRepository ratingRepository;
    @Mock
    private UserMediaActivityRepository userMediaActivityRepository;
    @Mock
    private UserMediaService userMediaService;
    @Mock
    private MediaSearchItemAssembler mediaSearchItemAssembler;
    @Mock
    private MediaConsumptionPolicy mediaConsumptionPolicy;

    @InjectMocks
    private ReviewService reviewService;

    @Test
    void createsReviewAndMarksMediaCompleted() {
        UUID userId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        User user = user(userId);
        Media media = media(mediaId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(mediaRepository.findById(mediaId)).thenReturn(Optional.of(media));
        when(reviewRepository.findByUserIdAndMediaId(userId, mediaId)).thenReturn(Optional.empty());
        when(reviewRepository.saveAndFlush(any(Review.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ReviewResponse response = reviewService.upsert(
                userId,
                mediaId,
                new UpsertReviewRequest(
                        new BigDecimal("4.5"),
                        "  Uma ótima descoberta.  ",
                        true,
                        Visibility.PUBLIC
                )
        );

        assertThat(response.rating()).isEqualByComparingTo("4.5");
        assertThat(response.content()).isEqualTo("Uma ótima descoberta.");
        assertThat(response.containsSpoilers()).isTrue();
        verify(userMediaService).markCompleted(user, media);
    }

    @Test
    void rejectsAReviewWithoutContent() {
        UUID userId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        assertThatThrownBy(() -> reviewService.upsert(
                userId,
                mediaId,
                new UpsertReviewRequest(new BigDecimal("3.0"), " ", true, Visibility.PRIVATE)
        )).isInstanceOf(ApiException.class).hasMessage("A resenha precisa ter conteúdo");

        verify(reviewRepository, never()).saveAndFlush(any());
    }

    @Test
    void editsTheUsersExistingReviewInsteadOfCreatingAnotherOne() {
        UUID userId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        User user = user(userId);
        Media media = media(mediaId);
        Review existing = new Review();
        existing.setId(UUID.randomUUID());
        existing.setUser(user);
        existing.setMedia(media);
        existing.setRating(new BigDecimal("2.0"));
        existing.setContent("Antes");
        existing.setVisibility(Visibility.PUBLIC);
        existing.setContainsSpoilers(false);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(mediaRepository.findById(mediaId)).thenReturn(Optional.of(media));
        when(reviewRepository.findByUserIdAndMediaId(userId, mediaId))
                .thenReturn(Optional.of(existing));
        when(reviewRepository.saveAndFlush(existing)).thenReturn(existing);

        ReviewResponse response = reviewService.upsert(
                userId,
                mediaId,
                new UpsertReviewRequest(
                        new BigDecimal("5.0"),
                        "  Depois  ",
                        false,
                        Visibility.PRIVATE
                )
        );

        assertThat(response.id()).isEqualTo(existing.getId());
        assertThat(response.rating()).isEqualByComparingTo("5.0");
        assertThat(response.content()).isEqualTo("Depois");
        assertThat(response.visibility()).isEqualTo(Visibility.PRIVATE);
        verify(reviewRepository).saveAndFlush(existing);
        verify(userMediaService).markCompleted(user, media);
    }

    @Test
    void createsAReviewFromTheUsersExistingStandaloneRating() {
        UUID userId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        User user = user(userId);
        Media media = media(mediaId);
        Rating rating = new Rating();
        rating.setUser(user);
        rating.setMedia(media);
        rating.setValue(new BigDecimal("3.5"));
        rating.setVisibility(Visibility.PUBLIC);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(mediaRepository.findById(mediaId)).thenReturn(Optional.of(media));
        when(reviewRepository.findByUserIdAndMediaId(userId, mediaId)).thenReturn(Optional.empty());
        when(ratingRepository.findByUserIdAndMediaId(userId, mediaId)).thenReturn(Optional.of(rating));
        when(reviewRepository.saveAndFlush(any(Review.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ReviewResponse response = reviewService.upsert(
                userId,
                mediaId,
                new UpsertReviewRequest(
                        new BigDecimal("4.5"),
                        "Agora com review",
                        false,
                        Visibility.PUBLIC
                )
        );

        assertThat(response.rating()).isEqualByComparingTo("4.5");
        verify(reviewRepository).saveAndFlush(argThat(review -> review.getRatingEntity() == rating));
    }

    @Test
    void rejectsRatingsOutsideHalfStarSteps() {
        UpsertReviewRequest request = new UpsertReviewRequest(
                new BigDecimal("4.2"),
                "Nota inválida",
                false,
                Visibility.PUBLIC
        );

        assertThatThrownBy(() -> reviewService.upsert(UUID.randomUUID(), UUID.randomUUID(), request))
                .isInstanceOf(ApiException.class)
                .hasMessage("A nota deve estar entre 0,5 e 5, em intervalos de meia estrela");

        verify(reviewRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsReviewingAnUnreleasedWork() {
        UUID userId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        User user = user(userId);
        Media futureMovie = media(mediaId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(mediaRepository.findById(mediaId)).thenReturn(Optional.of(futureMovie));
        doThrow(new ApiException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "MEDIA_NOT_RELEASED",
                "A obra ainda não foi lançada"
        )).when(mediaConsumptionPolicy).ensureReleased(futureMovie);

        assertThatThrownBy(() -> reviewService.upsert(
                userId,
                mediaId,
                new UpsertReviewRequest(
                        new BigDecimal("4.0"),
                        "Review antecipada",
                        false,
                        Visibility.PUBLIC
                )
        )).isInstanceOf(ApiException.class).hasMessage("A obra ainda não foi lançada");
        verify(reviewRepository, never()).saveAndFlush(any());
    }

    @Test
    void returnsOnlyPublicReviewsThroughPublicQuery() {
        UUID mediaId = UUID.randomUUID();
        Review review = new Review();
        review.setId(UUID.randomUUID());
        review.setMedia(media(mediaId));
        review.setUser(user(UUID.randomUUID()));
        review.setRating(new BigDecimal("5.0"));
        review.setVisibility(Visibility.PUBLIC);
        when(mediaRepository.existsById(mediaId)).thenReturn(true);
        when(reviewRepository.findByMediaIdAndVisibility(
                org.mockito.ArgumentMatchers.eq(mediaId),
                org.mockito.ArgumentMatchers.eq(Visibility.PUBLIC),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(review)));

        PageResponse<ReviewResponse> response = reviewService.findPublic(null, mediaId, 0, 10);

        assertThat(response.items()).hasSize(1);
        verify(reviewRepository).findByMediaIdAndVisibility(
                org.mockito.ArgumentMatchers.eq(mediaId),
                org.mockito.ArgumentMatchers.eq(Visibility.PUBLIC),
                org.mockito.ArgumentMatchers.eq(PageRequest.of(
                        0,
                        10,
                        Sort.by(Sort.Direction.DESC, "publishedAt")
                                .and(Sort.by(Sort.Direction.DESC, "id"))
                ))
        );
    }

    @Test
    void returnsTheThreeMostLikedPublicReviewsAsPopular() {
        UUID mediaId = UUID.randomUUID();
        List<Review> reviews = List.of(
                review(mediaId, "5.0"),
                review(mediaId, "4.5"),
                review(mediaId, "4.0")
        );
        when(mediaRepository.existsById(mediaId)).thenReturn(true);
        List<UUID> reviewIds = reviews.stream().map(Review::getId).toList();
        when(reviewRepository.findPopularIds(
                mediaId,
                Visibility.PUBLIC,
                PageRequest.of(0, 3)
        )).thenReturn(reviewIds);
        when(reviewRepository.findAllByIdIn(reviewIds)).thenReturn(reviews);

        List<ReviewResponse> response = reviewService.findPopular(null, mediaId);

        assertThat(response).extracting(ReviewResponse::rating)
                .containsExactly(
                        new BigDecimal("5.0"),
                        new BigDecimal("4.5"),
                        new BigDecimal("4.0")
                );
    }

    @Test
    void returnsGloballyPopularReviewsWithTheirMediaSummary() {
        UUID mediaId = UUID.randomUUID();
        Review review = review(mediaId, "4.5");
        review.setContent("Uma ótima descoberta.");
        when(reviewRepository.findGloballyPopularIds(
                Visibility.PUBLIC, PageRequest.of(0, 12)))
                .thenReturn(List.of(review.getId()));
        when(reviewRepository.findAllByIdIn(List.of(review.getId())))
                .thenReturn(List.of(review));
        MediaSearchItemResponse mediaItem = new MediaSearchItemResponse(
                mediaId, mediaId.toString(), ExternalSource.MANUAL, MediaType.MOVIE,
                "Central do Brasil", null, null, null, null, true, 4.5, 10
        );
        when(mediaSearchItemAssembler.fromImported(List.of(review.getMedia())))
                .thenReturn(List.of(mediaItem));

        var response = reviewService.findGloballyPopular(null, 12);

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().review().content()).isEqualTo("Uma ótima descoberta.");
        assertThat(response.getFirst().media().title()).isEqualTo("Central do Brasil");
    }

    @Test
    void returnsTheThreeMostRecentPublicReviews() {
        UUID mediaId = UUID.randomUUID();
        List<Review> reviews = List.of(
                review(mediaId, "3.0"),
                review(mediaId, "4.0"),
                review(mediaId, "5.0")
        );
        when(mediaRepository.existsById(mediaId)).thenReturn(true);
        when(reviewRepository.findTop3ByRatingMediaIdAndRatingVisibilityOrderByCreatedAtDescIdDesc(
                mediaId,
                Visibility.PUBLIC
        )).thenReturn(reviews);

        List<ReviewResponse> response = reviewService.findRecent(null, mediaId);

        assertThat(response).extracting(ReviewResponse::rating)
                .containsExactly(
                        new BigDecimal("3.0"),
                        new BigDecimal("4.0"),
                        new BigDecimal("5.0")
                );
    }

    @Test
    void rejectsReviewHighlightsForMissingMedia() {
        UUID mediaId = UUID.randomUUID();
        when(mediaRepository.existsById(mediaId)).thenReturn(false);

        assertThatThrownBy(() -> reviewService.findPopular(null, mediaId))
                .isInstanceOf(ApiException.class)
                .hasMessage("Mídia não encontrada");

        verify(reviewRepository, never())
                .findPopularIds(any(), any(), any());
    }

    @Test
    void deletesOnlyTheReviewOwnedByTheUser() {
        UUID userId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        Review review = new Review();
        when(reviewRepository.findByUserIdAndMediaId(userId, mediaId))
                .thenReturn(Optional.of(review));

        reviewService.delete(userId, mediaId);

        verify(reviewLikeRepository).deleteByReviewId(review.getId());
        verify(reviewRepository).delete(review);
    }

    @Test
    void deletingAMissingReviewIsIdempotent() {
        UUID userId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        when(reviewRepository.findByUserIdAndMediaId(userId, mediaId))
                .thenReturn(Optional.empty());

        reviewService.delete(userId, mediaId);

        verify(reviewRepository, never()).delete(any());
    }

    private User user(UUID id) {
        User user = new User();
        user.setId(id);
        user.setUsername("maria");
        user.setDisplayName("Maria");
        return user;
    }

    private Media media(UUID id) {
        Media media = new Media();
        media.setId(id);
        media.setType(MediaType.MOVIE);
        return media;
    }

    private Review review(UUID mediaId, String rating) {
        Review review = new Review();
        review.setId(UUID.randomUUID());
        review.setMedia(media(mediaId));
        review.setUser(user(UUID.randomUUID()));
        review.setRating(new BigDecimal(rating));
        review.setVisibility(Visibility.PUBLIC);
        return review;
    }
}
