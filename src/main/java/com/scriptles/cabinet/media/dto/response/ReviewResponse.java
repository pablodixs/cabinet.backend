package com.scriptles.cabinet.media.dto.response;

import com.scriptles.cabinet.media.entity.Review;
import com.scriptles.cabinet.user.enums.Visibility;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReviewResponse(
        UUID id,
        UUID mediaId,
        BigDecimal rating,
        String content,
        boolean containsSpoilers,
        Visibility visibility,
        Instant createdAt,
        Instant updatedAt,
        long likeCount,
        boolean liked,
        List<ReviewLikerResponse> recentLikers,
        AuthorResponse author,
        UUID activityId
) {
    public ReviewResponse(
            UUID id,
            UUID mediaId,
            BigDecimal rating,
            String content,
            boolean containsSpoilers,
            Visibility visibility,
            Instant createdAt,
            Instant updatedAt,
            long likeCount,
            boolean liked,
            List<ReviewLikerResponse> recentLikers,
            AuthorResponse author
    ) {
        this(id, mediaId, rating, content, containsSpoilers, visibility, createdAt, updatedAt,
                likeCount, liked, recentLikers, author, null);
    }

    public static ReviewResponse from(Review review) {
        return from(review, 0, false, List.of());
    }

    public static ReviewResponse from(
            Review review,
            long likeCount,
            boolean liked,
            List<ReviewLikerResponse> recentLikers
    ) {
        return new ReviewResponse(
                review.getId(),
                review.getMedia().getId(),
                review.getRating(),
                review.getContent(),
                Boolean.TRUE.equals(review.getContainsSpoilers()),
                review.getVisibility(),
                review.getPublishedAt() != null ? review.getPublishedAt() : review.getCreatedAt(),
                review.getUpdatedAt(),
                likeCount,
                liked,
                recentLikers,
                new AuthorResponse(
                        review.getUser().getId(),
                        review.getUser().getUsername(),
                        review.getUser().getDisplayName(),
                        review.getUser().getAvatarUlr()
                ),
                review.getActivity() == null ? null : review.getActivity().getId()
        );
    }

    public record AuthorResponse(
            UUID id,
            String username,
            String displayName,
            String avatarUrl
    ) {
    }
}
