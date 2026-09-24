package com.scriptles.cabinet.lists.dto.response;

import com.scriptles.cabinet.lists.entity.MediaList;
import com.scriptles.cabinet.user.enums.AccountTier;
import com.scriptles.cabinet.user.enums.Visibility;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import com.scriptles.cabinet.common.api.RichTextDocument;

public record MediaListResponse(
        UUID id,
        String name,
        String description,
        Visibility visibility,
        boolean ordered,
        String coverUrl,
        String backdropUrl,
        List<MediaListPreviewResponse> previewItems,
        long itemCount,
        Instant createdAt,
        Instant updatedAt,
        List<String> tags,
        JsonNode richDescription
) {
    public MediaListResponse(UUID id, String name, String description, Visibility visibility,
            boolean ordered, String coverUrl, String backdropUrl, List<MediaListPreviewResponse> previewItems,
            long itemCount, Instant createdAt, Instant updatedAt, List<String> tags) {
        this(id,name,description,visibility,ordered,coverUrl,backdropUrl,previewItems,itemCount,createdAt,updatedAt,tags,null);
    }
    public MediaListResponse(
            UUID id,
            String name,
            String description,
            Visibility visibility,
            boolean ordered,
            String coverUrl,
            List<MediaListPreviewResponse> previewItems,
            long itemCount,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(id, name, description, visibility, ordered, coverUrl, null,
                previewItems, itemCount, createdAt, updatedAt, List.of(), null);
    }

    public MediaListResponse(
            UUID id,
            String name,
            String description,
            Visibility visibility,
            boolean ordered,
            String coverUrl,
            String backdropUrl,
            List<MediaListPreviewResponse> previewItems,
            long itemCount,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(id, name, description, visibility, ordered, coverUrl, backdropUrl,
                previewItems, itemCount, createdAt, updatedAt, List.of(), null);
    }

    public static MediaListResponse from(MediaList list, long itemCount) {
        return from(list, itemCount, List.of());
    }

    public static MediaListResponse from(
            MediaList list,
            long itemCount,
            List<MediaListPreviewResponse> previewItems
    ) {
        return new MediaListResponse(
                list.getId(),
                list.getName(),
                list.getDescription(),
                list.getVisibility(),
                list.isOrdered(),
                list.getOwner().getAccountTier() == AccountTier.PRO
                        ? list.getCoverUrl() : null,
                list.getOwner().getAccountTier() == AccountTier.PRO
                        ? list.getBackdropUrl() : null,
                List.copyOf(previewItems),
                itemCount,
                list.getCreatedAt(),
                list.getUpdatedAt(),
                list.getTags().stream().map(tag -> tag.getName()).toList(),
                list.getRichDescription() == null ? null : RichTextDocument.parse(list.getRichDescription())
        );
    }
}
