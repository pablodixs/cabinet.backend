package com.scriptles.cabinet.media.dto.response;

import com.scriptles.cabinet.media.entity.Review;
import com.scriptles.cabinet.user.enums.Visibility;
import com.scriptles.cabinet.user.enums.AccountTier;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import com.scriptles.cabinet.common.api.RichTextDocument;

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
        UUID activityId,
        boolean likedByAuthor,
        boolean reconsumedByAuthor,
        String backdropKey,
        String backdropUrl,
        JsonNode richContent
) {
    public ReviewResponse(UUID id, UUID mediaId, BigDecimal rating, String content,
            boolean containsSpoilers, Visibility visibility, Instant createdAt, Instant updatedAt,
            long likeCount, boolean liked, List<ReviewLikerResponse> recentLikers,
            AuthorResponse author, UUID activityId, boolean likedByAuthor,
            boolean reconsumedByAuthor, String backdropKey, String backdropUrl) {
        this(id, mediaId, rating, content, containsSpoilers, visibility, createdAt, updatedAt,
                likeCount, liked, recentLikers, author, activityId, likedByAuthor,
                reconsumedByAuthor, backdropKey, backdropUrl, null);
    }
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
                likeCount, liked, recentLikers, author, null, false, false, null, null, null);
    }

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
            AuthorResponse author,
            UUID activityId,
            boolean likedByAuthor,
            boolean reconsumedByAuthor
    ) {
        this(id, mediaId, rating, content, containsSpoilers, visibility, createdAt, updatedAt,
                likeCount, liked, recentLikers, author, activityId, likedByAuthor,
                reconsumedByAuthor, null, null, null);
    }

    public static ReviewResponse from(Review review) {
        return from(review, 0, false, List.of(), false, false);
    }

    public static ReviewResponse from(
            Review review,
            long likeCount,
            boolean liked,
            List<ReviewLikerResponse> recentLikers
    ) {
        return from(review, likeCount, liked, recentLikers, false, false);
    }

    public static ReviewResponse from(
            Review review,
            long likeCount,
            boolean liked,
            List<ReviewLikerResponse> recentLikers,
            boolean likedByAuthor,
            boolean reconsumedByAuthor
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
                review.getAuthorProfile() == null
                    ? new AuthorResponse(review.getUser().getId(), review.getUser().getUsername(),
                        review.getUser().getDisplayName(), review.getUser().getAvatarUlr(),
                        review.getUser().getAccountTier() == AccountTier.PRO)
                    : new AuthorResponse(review.getAuthorProfile().getId(), review.getAuthorProfile().getHandle(),
                        review.getAuthorProfile().getDisplayName(), review.getAuthorProfile().getAvatarUrl(), false, true),
                review.getActivity() == null ? null : review.getActivity().getId(),
                likedByAuthor,
                reconsumedByAuthor,
                review.getBackdropKey(),
                review.getBackdropUrl(),
                review.getRichContent() == null ? null : RichTextDocument.parse(review.getRichContent())
        );
    }

    public record AuthorResponse(
            UUID id,
            String username,
            String displayName,
            String avatarUrl,
            boolean pro,
            boolean hq
    ) {
        public AuthorResponse(UUID id, String username, String displayName, String avatarUrl, boolean pro) {
            this(id, username, displayName, avatarUrl, pro, false);
        }
        public AuthorResponse(
                UUID id,
                String username,
                String displayName,
                String avatarUrl
        ) {
            this(id, username, displayName, avatarUrl, false, false);
        }
    }
}
