package com.scriptles.cabinet.lists.dto.response;

import com.scriptles.cabinet.lists.entity.MediaList;
import com.scriptles.cabinet.user.enums.Visibility;

import java.time.Instant;
import java.util.UUID;

public record MediaListResponse(
        UUID id,
        String name,
        String description,
        Visibility visibility,
        boolean ordered,
        String coverUrl,
        long itemCount,
        Instant createdAt,
        Instant updatedAt
) {
    public static MediaListResponse from(MediaList list, long itemCount) {
        return new MediaListResponse(
                list.getId(),
                list.getName(),
                list.getDescription(),
                list.getVisibility(),
                list.isOrdered(),
                list.getCoverUrl(),
                itemCount,
                list.getCreatedAt(),
                list.getUpdatedAt()
        );
    }
}
