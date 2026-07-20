package com.scriptles.cabinet.user.dto.response;

import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.user.entity.UserMedia;
import com.scriptles.cabinet.user.enums.UserMediaStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record LibraryMediaResponse(
        UUID id,
        UUID mediaId,
        UserMediaStatus status,
        MediaType type,
        String title,
        String coverUrl,
        LocalDate releaseDate,
        ExternalSource source,
        String externalId,
        Instant startedAt,
        Instant completedAt,
        Instant lastInteractionAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static LibraryMediaResponse from(
            UserMedia entry,
            ExternalReference externalReference
    ) {
        return from(entry, externalReference, entry.getMedia().getCoverUrl());
    }

    public static LibraryMediaResponse from(
            UserMedia entry,
            ExternalReference externalReference,
            String coverUrl
    ) {
        Media media = entry.getMedia();
        return new LibraryMediaResponse(
                entry.getId(),
                media.getId(),
                entry.getStatus(),
                media.getType(),
                media.getTitle(),
                coverUrl,
                media.getReleaseDate(),
                externalReference == null ? null : externalReference.getSource(),
                externalReference == null ? null : externalReference.getExternalId(),
                entry.getStartedAt(),
                entry.getCompletedAt(),
                entry.getLastInteractionAt(),
                entry.getCreatedAt(),
                entry.getUpdatedAt()
        );
    }
}
