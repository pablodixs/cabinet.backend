package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.media.dto.response.ReviewLikeResponse;
import com.scriptles.cabinet.media.dto.response.ReviewLikerResponse;
import com.scriptles.cabinet.media.entity.Review;
import com.scriptles.cabinet.media.repository.ReviewLikeRepository;
import com.scriptles.cabinet.media.repository.ReviewRepository;
import com.scriptles.cabinet.notifications.service.NotificationService;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.enums.Visibility;
import com.scriptles.cabinet.user.repository.UserRepository;
import com.scriptles.cabinet.user.service.SocialAccessPolicy;
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
    private final SocialAccessPolicy socialAccessPolicy;

    @Transactional(readOnly = true)
    public ReviewLikeResponse find(UUID userId, UUID reviewId) {
        findAccessibleReview(userId, reviewId);
        return response(userId, reviewId);
    }

    @Transactional
    public ReviewLikeResponse like(UUID userId, UUID reviewId) {
        Review review = findAccessibleReview(userId, reviewId);
        if (reviewLikeRepository.existsByUserIdAndReviewId(userId, reviewId)) {
            return response(userId, reviewId);
        }
        User actor = findUser(userId);
        int inserted = reviewLikeRepository.insertIfAbsent(UUID.randomUUID(), userId, reviewId);
        if (inserted > 0) {
            if (notificationService != null) notificationService.syncReviewLike(review, actor);
        }

        return response(userId, reviewId);
    }

    @Transactional
    public ReviewLikeResponse unlike(UUID userId, UUID reviewId) {
        Review review = findAccessibleReview(userId, reviewId);
        long deleted = reviewLikeRepository.deleteByUserIdAndReviewId(userId, reviewId);
        if (deleted > 0 && notificationService != null) {
            notificationService.syncReviewLike(review, findUser(userId));
        }
        return response(userId, reviewId);
    }

    private ReviewLikeResponse response(UUID userId, UUID reviewId) {
        var recentLikers = socialAccessPolicy == null
                ? reviewLikeRepository.findRecentLikers(List.of(reviewId))
                : reviewLikeRepository.findRecentLikersVisibleTo(List.of(reviewId), userId);
        return new ReviewLikeResponse(
                reviewLikeRepository.existsByUserIdAndReviewId(userId, reviewId),
                reviewLikeRepository.countByReviewId(reviewId),
                recentLikers.stream()
                        .map(liker -> new ReviewLikerResponse(
                                UUID.fromString(liker.getUserId()),
                                liker.getUsername(),
                                liker.getAvatarUrl()
                        ))
                        .toList()
        );
    }

    private Review findAccessibleReview(UUID userId, UUID reviewId) {
        return (socialAccessPolicy == null
                ? reviewRepository.findByIdAndVisibility(reviewId, Visibility.PUBLIC)
                : reviewRepository.findById(reviewId)
                    .filter(review -> socialAccessPolicy.canViewContent(
                            review.getUser().getId(), userId, review.getVisibility())))
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
