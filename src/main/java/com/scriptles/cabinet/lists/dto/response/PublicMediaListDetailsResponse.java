package com.scriptles.cabinet.lists.dto.response;

import com.scriptles.cabinet.lists.entity.MediaList;
import com.scriptles.cabinet.user.enums.Visibility;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PublicMediaListDetailsResponse(
        UUID id,
        String name,
        String description,
        Visibility visibility,
        boolean ordered,
        String coverUrl,
        long itemCount,
        long likeCount,
        boolean liked,
        boolean ownList,
        Instant createdAt,
        Instant updatedAt,
        PublicMediaListResponse.AuthorResponse owner,
        List<MediaListItemResponse> items
) {
    public static PublicMediaListDetailsResponse from(
            MediaList list,
            List<MediaListItemResponse> items,
            long likeCount,
            boolean liked,
            boolean ownList
    ) {
        return new PublicMediaListDetailsResponse(
                list.getId(),
                list.getName(),
                list.getDescription(),
                list.getVisibility(),
                list.isOrdered(),
                list.getCoverUrl(),
                items.size(),
                likeCount,
                liked,
                ownList,
                list.getCreatedAt(),
                list.getUpdatedAt(),
                new PublicMediaListResponse.AuthorResponse(
                        list.getOwner().getId(),
                        list.getOwner().getUsername(),
                        list.getOwner().getDisplayName(),
                        list.getOwner().getAvatarUlr()
                ),
                items
        );
    }
}
