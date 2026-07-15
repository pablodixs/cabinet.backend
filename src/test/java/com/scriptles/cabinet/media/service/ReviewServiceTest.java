package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.media.dto.request.UpsertReviewRequest;
import com.scriptles.cabinet.media.dto.response.ReviewResponse;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.Review;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.media.repository.ReviewRepository;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.enums.Visibility;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {
    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private MediaRepository mediaRepository;
    @Mock
    private UserMediaService userMediaService;

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
    void ratingOnlyReviewCannotContainSpoilers() {
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
                new UpsertReviewRequest(new BigDecimal("3.0"), " ", true, Visibility.PRIVATE)
        );

        assertThat(response.content()).isNull();
        assertThat(response.containsSpoilers()).isFalse();
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
    void rejectsRatingsOutsideHalfStarSteps() {
        UpsertReviewRequest request = new UpsertReviewRequest(
                new BigDecimal("4.2"),
                null,
                false,
                Visibility.PUBLIC
        );

        assertThatThrownBy(() -> reviewService.upsert(UUID.randomUUID(), UUID.randomUUID(), request))
                .isInstanceOf(ApiException.class)
                .hasMessage("A nota deve estar entre 0,5 e 5, em intervalos de meia estrela");

        verify(reviewRepository, never()).saveAndFlush(any());
    }

    @Test
    void returnsOnlyPublicReviewsThroughPublicQuery() {
        UUID mediaId = UUID.randomUUID();
        Review review = new Review();
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

        PageResponse<ReviewResponse> response = reviewService.findPublic(mediaId, 0, 10);

        assertThat(response.items()).hasSize(1);
        verify(reviewRepository).findByMediaIdAndVisibility(
                org.mockito.ArgumentMatchers.eq(mediaId),
                org.mockito.ArgumentMatchers.eq(Visibility.PUBLIC),
                org.mockito.ArgumentMatchers.eq(PageRequest.of(
                        0,
                        10,
                        Sort.by(Sort.Direction.DESC, "createdAt")
                                .and(Sort.by(Sort.Direction.DESC, "id"))
                ))
        );
    }

    @Test
    void deletesOnlyTheReviewOwnedByTheUser() {
        UUID userId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        Review review = new Review();
        when(reviewRepository.findByUserIdAndMediaId(userId, mediaId))
                .thenReturn(Optional.of(review));

        reviewService.delete(userId, mediaId);

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
}
