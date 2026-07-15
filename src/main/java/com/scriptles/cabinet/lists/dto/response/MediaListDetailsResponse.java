package com.scriptles.cabinet.lists.dto.response;

import com.scriptles.cabinet.lists.entity.MediaList;
import com.scriptles.cabinet.user.enums.Visibility;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MediaListDetailsResponse(
        UUID id,
        String name,
        String description,
        Visibility visibility,
        boolean ordered,
        String coverUrl,
        long itemCount,
        Instant createdAt,
        Instant updatedAt,
        List<MediaListItemResponse> items
) {
    public static MediaListDetailsResponse from(
            MediaList list,
            List<MediaListItemResponse> items
    ) {
        return new MediaListDetailsResponse(
                list.getId(),
                list.getName(),
                list.getDescription(),
                list.getVisibility(),
                list.isOrdered(),
                list.getCoverUrl(),
                items.size(),
                list.getCreatedAt(),
                list.getUpdatedAt(),
                items
        );
    }
}
