package com.scriptles.cabinet.comments.dto;

import com.scriptles.cabinet.comments.entity.Comment;
import com.scriptles.cabinet.user.enums.AccountTier;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CommentResponse(
        UUID id,
        String content,
        boolean deleted,
        boolean canEdit,
        Instant createdAt,
        Instant updatedAt,
        AuthorResponse author,
        List<CommentResponse> replies
) {
    public static CommentResponse from(Comment comment, UUID viewerId, List<CommentResponse> replies) {
        boolean deleted = comment.getDeletedAt() != null;
        return new CommentResponse(
                comment.getId(),
                deleted ? null : comment.getContent(),
                deleted,
                !deleted && viewerId != null && viewerId.equals(comment.getAuthor().getId()),
                comment.getCreatedAt(),
                comment.getUpdatedAt(),
                new AuthorResponse(
                        comment.getAuthor().getId(),
                        comment.getAuthor().getUsername(),
                        comment.getAuthor().getDisplayName(),
                        comment.getAuthor().getAvatarUlr(),
                        comment.getAuthor().getAccountTier() == AccountTier.PRO
                ),
                replies
        );
    }

    public record AuthorResponse(
            UUID id,
            String username,
            String displayName,
            String avatarUrl,
            boolean pro
    ) {
    }
}
