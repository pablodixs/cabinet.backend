package com.scriptles.cabinet.lists.dto.response;

import com.scriptles.cabinet.lists.entity.MediaList;
import com.scriptles.cabinet.lists.entity.MediaListItem;

import java.time.Instant;
import java.util.UUID;

public record PublicMediaListResponse(
        UUID id,
        String name,
        String description,
        boolean ordered,
        String coverUrl,
        long itemCount,
        long likeCount,
        int mediaPosition,
        Instant updatedAt,
        AuthorResponse owner
) {
    public static PublicMediaListResponse from(
            MediaList list,
            MediaListItem item,
            long itemCount,
            long likeCount
    ) {
        return new PublicMediaListResponse(
                list.getId(),
                list.getName(),
                list.getDescription(),
                list.isOrdered(),
                list.getCoverUrl(),
                itemCount,
                likeCount,
                item.getPosition(),
                list.getUpdatedAt(),
                new AuthorResponse(
                        list.getOwner().getId(),
                        list.getOwner().getUsername(),
                        list.getOwner().getDisplayName(),
                        list.getOwner().getAvatarUlr()
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
