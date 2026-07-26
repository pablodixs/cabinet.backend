package com.scriptles.cabinet.lists.dto.response;

import com.scriptles.cabinet.lists.entity.MediaList;
import com.scriptles.cabinet.user.enums.AccountTier;
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
        String backdropUrl,
        long itemCount,
        Long consumedItemCount,
        Integer consumedPercentage,
        long likeCount,
        boolean liked,
        boolean ownList,
        Instant createdAt,
        Instant updatedAt,
        PublicMediaListResponse.AuthorResponse owner,
        List<MediaListItemResponse> items
) {
    public PublicMediaListDetailsResponse(
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
        this(id, name, description, visibility, ordered, coverUrl, null,
                itemCount, null, null, likeCount, liked, ownList, createdAt, updatedAt, owner, items);
    }

    public static PublicMediaListDetailsResponse from(
            MediaList list,
            List<MediaListItemResponse> items,
            long likeCount,
            boolean liked,
            boolean ownList
    ) {
        return from(list, items, likeCount, liked, ownList, null, null);
    }

    public static PublicMediaListDetailsResponse from(
            MediaList list,
            List<MediaListItemResponse> items,
            long likeCount,
            boolean liked,
            boolean ownList,
            Long consumedItemCount,
            Integer consumedPercentage
    ) {
        return new PublicMediaListDetailsResponse(
                list.getId(),
                list.getName(),
                list.getDescription(),
                list.getVisibility(),
                list.isOrdered(),
                list.getCoverUrl(),
                list.getOwner().getAccountTier() == AccountTier.PRO
                        ? list.getBackdropUrl() : null,
                items.size(),
                consumedItemCount,
                consumedPercentage,
                likeCount,
                liked,
                ownList,
                list.getCreatedAt(),
                list.getUpdatedAt(),
                new PublicMediaListResponse.AuthorResponse(
                        list.getOwner().getId(),
                        list.getOwner().getUsername(),
                        list.getOwner().getDisplayName(),
                        list.getOwner().getAvatarUlr(),
                        list.getOwner().getAccountTier() == AccountTier.PRO
                ),
                items
        );
    }
}
