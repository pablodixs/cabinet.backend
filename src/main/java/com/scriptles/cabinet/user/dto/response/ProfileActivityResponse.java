package com.scriptles.cabinet.user.dto.response;

import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.user.entity.UserMedia;
import com.scriptles.cabinet.user.entity.UserMediaActivity;
import com.scriptles.cabinet.user.enums.ProfileActivityType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.math.BigDecimal;
import java.util.List;
import java.time.ZoneId;

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
        String externalId,
        LocalDate occurredOn,
        LocalDate loggedOn,
        BigDecimal rating,
        String review,
        List<String> tags,
        boolean containsSpoilers,
        boolean liked,
        boolean hasReview
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
                externalReference == null ? null : externalReference.getExternalId(),
                occurredAt == null ? null : occurredAt.atZone(ZoneId.of("America/Sao_Paulo")).toLocalDate(),
                null,
                null,
                null,
                List.of(),
                false,
                false,
                false
        );
    }

    public static ProfileActivityResponse from(
            UserMediaActivity activity,
            ExternalReference externalReference
    ) {
        return from(activity, externalReference, activity.getMedia().getCoverUrl());
    }

    public static ProfileActivityResponse from(
            UserMediaActivity activity,
            ExternalReference externalReference,
            String coverUrl
    ) {
        return from(activity, externalReference, coverUrl, false);
    }

    public static ProfileActivityResponse from(
            UserMediaActivity activity,
            ExternalReference externalReference,
            String coverUrl,
            boolean liked
    ) {
        Media media = activity.getMedia();
        Instant occurredAt = activity.getOccurredOn()
                .atStartOfDay(ZoneId.of("America/Sao_Paulo"))
                .toInstant();
        return new ProfileActivityResponse(
                activity.getId(),
                activity.getType(),
                occurredAt,
                media.getId(),
                media.getType(),
                media.getTitle(),
                coverUrl,
                media.getReleaseDate(),
                externalReference == null ? null : externalReference.getSource(),
                externalReference == null ? null : externalReference.getExternalId(),
                activity.getOccurredOn(),
                activity.getLoggedOn(),
                activity.getRating(),
                activity.getReviewContent(),
                List.copyOf(activity.getTags()),
                Boolean.TRUE.equals(activity.getContainsSpoilers()),
                liked,
                activity.getReviewContent() != null
                        && !activity.getReviewContent().isBlank()
        );
    }
}
