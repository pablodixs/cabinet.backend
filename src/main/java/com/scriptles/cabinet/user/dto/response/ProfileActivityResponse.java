package com.scriptles.cabinet.user.dto.response;

import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.user.entity.UserMedia;
import com.scriptles.cabinet.user.enums.ProfileActivityType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ProfileActivityResponse(
        UUID id,
        ProfileActivityType type,
        Instant occurredAt,
        UUID mediaId,
        MediaType mediaType,
        String title,
        String coverUrl,
        LocalDate releaseDate,
        ExternalSource source,
        String externalId
) {
    public static ProfileActivityResponse from(
            UserMedia entry,
            ExternalReference externalReference
    ) {
        Media media = entry.getMedia();
        Instant occurredAt = entry.getLastInteractionAt() != null
                ? entry.getLastInteractionAt()
                : entry.getUpdatedAt() != null
                    ? entry.getUpdatedAt()
                    : entry.getCreatedAt();

        return new ProfileActivityResponse(
                entry.getId(),
                switch (entry.getStatus()) {
                    case PLANNED -> ProfileActivityType.ADDED_TO_LIBRARY;
                    case IN_PROGRESS -> ProfileActivityType.STARTED;
                    case COMPLETED -> ProfileActivityType.COMPLETED;
                    case PAUSED -> ProfileActivityType.PAUSED;
                    case DROPPED -> ProfileActivityType.DROPPED;
                },
                occurredAt,
                media.getId(),
                media.getType(),
                media.getTitle(),
                media.getCoverUrl(),
                media.getReleaseDate(),
                externalReference == null ? null : externalReference.getSource(),
                externalReference == null ? null : externalReference.getExternalId()
        );
    }
}
