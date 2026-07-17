package com.scriptles.cabinet.lists.dto.response;

import com.scriptles.cabinet.lists.entity.MediaList;
import com.scriptles.cabinet.user.enums.Visibility;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MediaListResponse(
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
                list.getCoverUrl(),
                List.copyOf(previewItems),
                itemCount,
                list.getCreatedAt(),
                list.getUpdatedAt()
        );
    }
}
