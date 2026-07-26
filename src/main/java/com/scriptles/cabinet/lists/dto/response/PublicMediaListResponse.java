package com.scriptles.cabinet.lists.dto.response;

import com.scriptles.cabinet.lists.entity.MediaList;
import com.scriptles.cabinet.lists.entity.MediaListItem;
import com.scriptles.cabinet.user.enums.AccountTier;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PublicMediaListResponse(
        UUID id,
        String name,
        String description,
        boolean ordered,
        String coverUrl,
        String backdropUrl,
        List<MediaListPreviewResponse> previewItems,
        long itemCount,
        Long consumedItemCount,
        Integer consumedPercentage,
        long likeCount,
        int mediaPosition,
        Instant updatedAt,
        AuthorResponse owner
) {
    public PublicMediaListResponse(
            UUID id,
            String name,
            String description,
            boolean ordered,
            String coverUrl,
            List<MediaListPreviewResponse> previewItems,
            long itemCount,
            long likeCount,
            int mediaPosition,
            Instant updatedAt,
            AuthorResponse owner
    ) {
        this(id, name, description, ordered, coverUrl, null, previewItems,
                itemCount, null, null, likeCount, mediaPosition, updatedAt, owner);
    }

    public static PublicMediaListResponse from(
            MediaList list,
            MediaListItem item,
            long itemCount,
            long likeCount,
            List<MediaListPreviewResponse> previewItems
    ) {
        return from(list, item, itemCount, likeCount, previewItems, null, null);
    }

    public static PublicMediaListResponse from(
            MediaList list,
            MediaListItem item,
            long itemCount,
            long likeCount,
            List<MediaListPreviewResponse> previewItems,
            Long consumedItemCount,
            Integer consumedPercentage
    ) {
        return new PublicMediaListResponse(
                list.getId(),
                list.getName(),
                list.getDescription(),
                list.isOrdered(),
                list.getCoverUrl(),
                list.getOwner().getAccountTier() == AccountTier.PRO
                        ? list.getBackdropUrl() : null,
                List.copyOf(previewItems),
                itemCount,
                consumedItemCount,
                consumedPercentage,
                likeCount,
                item.getPosition(),
                list.getUpdatedAt(),
                new AuthorResponse(
                        list.getOwner().getId(),
                        list.getOwner().getUsername(),
                        list.getOwner().getDisplayName(),
                        list.getOwner().getAvatarUlr(),
                        list.getOwner().getAccountTier() == AccountTier.PRO
                )
        );
    }

    public record AuthorResponse(
            UUID id,
            String username,
            String displayName,
            String avatarUrl,
            boolean pro
    ) {
        public AuthorResponse(
                UUID id,
                String username,
                String displayName,
                String avatarUrl
        ) {
            this(id, username, displayName, avatarUrl, false);
        }
    }
}
