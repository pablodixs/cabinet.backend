package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.media.dto.response.ReviewLikeResponse;
import com.scriptles.cabinet.media.dto.response.ReviewLikerResponse;
import com.scriptles.cabinet.media.entity.Review;
import com.scriptles.cabinet.media.entity.ReviewLike;
import com.scriptles.cabinet.media.repository.ReviewLikeRepository;
import com.scriptles.cabinet.media.repository.ReviewRepository;
import com.scriptles.cabinet.notifications.service.NotificationService;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.enums.Visibility;
import com.scriptles.cabinet.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReviewLikeService {
    private final ReviewLikeRepository reviewLikeRepository;
    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public ReviewLikeResponse find(UUID userId, UUID reviewId) {
        findPublicReview(reviewId);
        return response(userId, reviewId);
    }

    @Transactional
    public ReviewLikeResponse like(UUID userId, UUID reviewId) {
        Review review = findPublicReview(reviewId);
        if (!reviewLikeRepository.existsByUserIdAndReviewId(userId, reviewId)) {
            User actor = findUser(userId);
            ReviewLike like = new ReviewLike();
            like.setUser(actor);
            like.setReview(review);
            reviewLikeRepository.saveAndFlush(like);
            if (notificationService != null) notificationService.syncReviewLike(review, actor);
        }

        return response(userId, reviewId);
    }

    @Transactional
    public ReviewLikeResponse unlike(UUID userId, UUID reviewId) {
        Review review = findPublicReview(reviewId);
        long deleted = reviewLikeRepository.deleteByUserIdAndReviewId(userId, reviewId);
        if (deleted > 0 && notificationService != null) {
            notificationService.syncReviewLike(review, findUser(userId));
        }
        return response(userId, reviewId);
    }

    private ReviewLikeResponse response(UUID userId, UUID reviewId) {
        return new ReviewLikeResponse(
                reviewLikeRepository.existsByUserIdAndReviewId(userId, reviewId),
                reviewLikeRepository.countByReviewId(reviewId),
                reviewLikeRepository.findRecentLikers(List.of(reviewId))
                        .stream()
                        .map(liker -> new ReviewLikerResponse(
                                UUID.fromString(liker.getUserId()),
                                liker.getUsername(),
                                liker.getAvatarUrl()
                        ))
                        .toList()
        );
    }

    private Review findPublicReview(UUID reviewId) {
        return reviewRepository.findByIdAndVisibility(reviewId, Visibility.PUBLIC)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "REVIEW_NOT_FOUND",
                        "Review não encontrada"
                ));
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "USER_NOT_FOUND",
                "Usuário não encontrado"
        ));
    }
}
