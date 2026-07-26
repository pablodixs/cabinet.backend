package com.scriptles.cabinet.lists.dto.response;

import com.scriptles.cabinet.lists.entity.MediaListItem;
import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record MediaListItemResponse(
        UUID id,
        UUID mediaId,
        int position,
        String notes,
        MediaType type,
        String title,
        String coverUrl,
        LocalDate releaseDate,
        ExternalSource source,
        String externalId,
        Instant createdAt,
        boolean consumed
) {
    public static MediaListItemResponse from(
            MediaListItem item,
            ExternalReference externalReference
    ) {
        return from(item, externalReference, item.getMedia().getCoverUrl());
    }

    public static MediaListItemResponse from(
            MediaListItem item,
            ExternalReference externalReference,
            String coverUrl
    ) {
        return from(item, externalReference, coverUrl, false);
    }

    public static MediaListItemResponse from(
            MediaListItem item,
            ExternalReference externalReference,
            String coverUrl,
            boolean consumed
    ) {
        Media media = item.getMedia();
        return new MediaListItemResponse(
                item.getId(),
                media.getId(),
                item.getPosition(),
                item.getNotes(),
                media.getType(),
                media.getTitle(),
                coverUrl,
                media.getReleaseDate(),
                externalReference == null ? null : externalReference.getSource(),
                externalReference == null ? null : externalReference.getExternalId(),
                item.getCreatedAt(),
                consumed
        );
    }
}
