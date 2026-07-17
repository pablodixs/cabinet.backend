package com.scriptles.cabinet.lists.dto.response;

import com.scriptles.cabinet.lists.entity.MediaList;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PublicListSearchResponse(
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
    public static PublicListSearchResponse from(
            MediaList list,
            long itemCount,
            long likeCount,
            List<MediaListPreviewResponse> previewItems
    ) {
        return new PublicListSearchResponse(
                list.getId(),
                list.getName(),
                list.getDescription(),
                list.isOrdered(),
                list.getCoverUrl(),
                List.copyOf(previewItems),
                itemCount,
                likeCount,
                list.getUpdatedAt(),
                new PublicMediaListResponse.AuthorResponse(
                        list.getOwner().getId(),
                        list.getOwner().getUsername(),
                        list.getOwner().getDisplayName(),
                        list.getOwner().getAvatarUlr()
                )
        );
    }
}
