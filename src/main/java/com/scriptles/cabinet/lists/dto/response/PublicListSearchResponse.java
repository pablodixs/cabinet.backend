package com.scriptles.cabinet.lists.dto.response;

import com.scriptles.cabinet.lists.entity.MediaList;
import com.scriptles.cabinet.user.enums.AccountTier;
import com.scriptles.cabinet.common.api.RichTextDocument;
import tools.jackson.databind.JsonNode;

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
        PublicMediaListResponse.AuthorResponse owner,
        List<String> tags,
        JsonNode richDescription
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
                itemCount, null, null, likeCount, updatedAt, owner, List.of(), null);
    }

    public PublicListSearchResponse(
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
        this(id, name, description, ordered, coverUrl, backdropUrl,
                previewItems, itemCount, consumedItemCount, consumedPercentage,
                likeCount, updatedAt, owner, List.of(), null);
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
                list.getOwner().getAccountTier() == AccountTier.PRO
                        ? list.getCoverUrl() : null,
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
                ),
                list.getTags().stream().map(tag -> tag.getName()).toList(),
                list.getRichDescription() == null ? null : RichTextDocument.parse(list.getRichDescription())
        );
    }
}
