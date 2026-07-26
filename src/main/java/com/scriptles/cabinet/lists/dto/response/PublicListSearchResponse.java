package com.scriptles.cabinet.lists.dto.response;

import com.scriptles.cabinet.lists.entity.MediaList;
import com.scriptles.cabinet.user.enums.AccountTier;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PublicListSearchResponse(
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
        Instant updatedAt,
        PublicMediaListResponse.AuthorResponse owner
) {
    public PublicListSearchResponse(
            UUID id,
            String name,
            String description,
            boolean ordered,
            String coverUrl,
            List<MediaListPreviewResponse> previewItems,
            long itemCount,
            long likeCount,
            Instant updatedAt,
            PublicMediaListResponse.AuthorResponse owner
    ) {
        this(id, name, description, ordered, coverUrl, null, previewItems,
                itemCount, null, null, likeCount, updatedAt, owner);
    }

    public static PublicListSearchResponse from(
            MediaList list,
            long itemCount,
            long likeCount,
            List<MediaListPreviewResponse> previewItems
    ) {
        return from(list, itemCount, likeCount, previewItems, null, null);
    }

    public static PublicListSearchResponse from(
            MediaList list,
            long itemCount,
            long likeCount,
            List<MediaListPreviewResponse> previewItems,
            Long consumedItemCount,
            Integer consumedPercentage
    ) {
        return new PublicListSearchResponse(
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
                list.getUpdatedAt(),
                new PublicMediaListResponse.AuthorResponse(
                        list.getOwner().getId(),
                        list.getOwner().getUsername(),
                        list.getOwner().getDisplayName(),
                        list.getOwner().getAvatarUlr(),
                        list.getOwner().getAccountTier() == AccountTier.PRO
                )
        );
    }
}
