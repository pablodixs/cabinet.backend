package com.scriptles.cabinet.lists.dto.response;

import com.scriptles.cabinet.lists.entity.MediaList;
import com.scriptles.cabinet.user.enums.AccountTier;
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
        String backdropUrl,
        long itemCount,
        Instant createdAt,
        Instant updatedAt,
        List<MediaListItemResponse> items,
        List<String> tags
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
                list.getOwner().getAccountTier() == AccountTier.PRO
                        ? list.getCoverUrl() : null,
                list.getOwner().getAccountTier() == AccountTier.PRO
                        ? list.getBackdropUrl() : null,
                items.size(),
                list.getCreatedAt(),
                list.getUpdatedAt(),
                items,
                list.getTags().stream().map(tag -> tag.getName()).toList()
        );
    }
}
