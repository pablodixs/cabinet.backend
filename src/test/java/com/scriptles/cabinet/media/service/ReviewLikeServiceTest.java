package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.media.entity.Review;
import com.scriptles.cabinet.media.repository.ReviewLikeRepository;
import com.scriptles.cabinet.media.repository.ReviewRepository;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.enums.Visibility;
import com.scriptles.cabinet.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewLikeServiceTest {
    @Mock
    private ReviewLikeRepository reviewLikeRepository;
    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ReviewLikeService reviewLikeService;

    @Test
    void likesPublicReviewAndReturnsUpdatedCount() {
        UUID userId = UUID.randomUUID();
        UUID reviewId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        Review review = publicReview(reviewId);

        when(reviewRepository.findByIdAndVisibility(reviewId, Visibility.PUBLIC))
                .thenReturn(Optional.of(review));
        when(reviewLikeRepository.existsByUserIdAndReviewId(userId, reviewId))
                .thenReturn(false, true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(reviewLikeRepository.insertIfAbsent(any(UUID.class), eq(userId), eq(reviewId)))
                .thenReturn(1);
        when(reviewLikeRepository.countByReviewId(reviewId)).thenReturn(7L);
        ReviewLikeRepository.RecentReviewLiker recentLiker = mock(
                ReviewLikeRepository.RecentReviewLiker.class
        );
        UUID recentLikerId = UUID.randomUUID();
        when(recentLiker.getUserId()).thenReturn(recentLikerId.toString());
        when(recentLiker.getUsername()).thenReturn("ana");
        when(recentLiker.getAvatarUrl()).thenReturn("https://example.com/ana.jpg");
        when(reviewLikeRepository.findRecentLikers(List.of(reviewId)))
                .thenReturn(List.of(recentLiker));

        var response = reviewLikeService.like(userId, reviewId);

        verify(reviewLikeRepository).insertIfAbsent(any(UUID.class), eq(userId), eq(reviewId));
        assertThat(response.liked()).isTrue();
        assertThat(response.likeCount()).isEqualTo(7);
        assertThat(response.recentLikers()).singleElement().satisfies(liker -> {
            assertThat(liker.id()).isEqualTo(recentLikerId);
            assertThat(liker.username()).isEqualTo("ana");
            assertThat(liker.avatarUrl()).isEqualTo("https://example.com/ana.jpg");
        });
    }

    @Test
    void repeatedLikeIsIdempotent() {
        UUID userId = UUID.randomUUID();
        UUID reviewId = UUID.randomUUID();
        when(reviewRepository.findByIdAndVisibility(reviewId, Visibility.PUBLIC))
                .thenReturn(Optional.of(publicReview(reviewId)));
        when(reviewLikeRepository.existsByUserIdAndReviewId(userId, reviewId))
                .thenReturn(true);
        when(reviewLikeRepository.countByReviewId(reviewId)).thenReturn(3L);

        var response = reviewLikeService.like(userId, reviewId);

        assertThat(response.liked()).isTrue();
        assertThat(response.likeCount()).isEqualTo(3);
        verify(reviewLikeRepository, never()).insertIfAbsent(any(), any(), any());
        verify(userRepository, never()).findById(userId);
    }

    @Test
    void unlikesReviewIdempotently() {
        UUID userId = UUID.randomUUID();
        UUID reviewId = UUID.randomUUID();
        when(reviewRepository.findByIdAndVisibility(reviewId, Visibility.PUBLIC))
                .thenReturn(Optional.of(publicReview(reviewId)));
        when(reviewLikeRepository.existsByUserIdAndReviewId(userId, reviewId))
                .thenReturn(false);
        when(reviewLikeRepository.countByReviewId(reviewId)).thenReturn(2L);

        var response = reviewLikeService.unlike(userId, reviewId);

        verify(reviewLikeRepository).deleteByUserIdAndReviewId(userId, reviewId);
        assertThat(response.liked()).isFalse();
        assertThat(response.likeCount()).isEqualTo(2);
    }

    @Test
    void hidesPrivateOrMissingReviews() {
        UUID userId = UUID.randomUUID();
        UUID reviewId = UUID.randomUUID();
        when(reviewRepository.findByIdAndVisibility(reviewId, Visibility.PUBLIC))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewLikeService.like(userId, reviewId))
                .isInstanceOf(ApiException.class)
                .hasMessage("Review não encontrada");

        verify(reviewLikeRepository, never()).insertIfAbsent(any(), any(), any());
    }

    private Review publicReview(UUID reviewId) {
        Review review = new Review();
        review.setId(reviewId);
        review.setVisibility(Visibility.PUBLIC);
        return review;
    }
}
