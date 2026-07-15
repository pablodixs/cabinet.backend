package com.scriptles.cabinet.media.dto.response;

import com.scriptles.cabinet.media.entity.Review;
import com.scriptles.cabinet.user.enums.Visibility;

import java.math.BigDecimal;
import java.time.Instant;
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
        AuthorResponse author
) {
    public static ReviewResponse from(Review review) {
        return new ReviewResponse(
                review.getId(),
                review.getMedia().getId(),
                review.getRating(),
                review.getContent(),
                Boolean.TRUE.equals(review.getContainsSpoilers()),
                review.getVisibility(),
                review.getCreatedAt(),
                review.getUpdatedAt(),
                new AuthorResponse(
                        review.getUser().getId(),
                        review.getUser().getUsername(),
                        review.getUser().getDisplayName(),
                        review.getUser().getAvatarUlr()
                )
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
