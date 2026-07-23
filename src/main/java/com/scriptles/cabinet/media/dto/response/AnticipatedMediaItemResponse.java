package com.scriptles.cabinet.media.dto.response;

import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;

import java.time.LocalDate;
import java.util.UUID;

public record AnticipatedMediaItemResponse(
        UUID id,
        String externalId,
        ExternalSource source,
        MediaType type,
        String title,
        String creator,
        String description,
        String coverUrl,
        LocalDate releaseDate,
        boolean imported,
        Double averageRating,
        long ratingCount,
        long plannedCount
) {
    public static AnticipatedMediaItemResponse from(
            MediaSearchItemResponse item,
            long plannedCount
    ) {
        return new AnticipatedMediaItemResponse(
                item.id(),
                item.externalId(),
                item.source(),
                item.type(),
                item.title(),
                item.creator(),
                item.description(),
                item.coverUrl(),
                item.releaseDate(),
                item.imported(),
                item.averageRating(),
                item.ratingCount(),
                plannedCount
        );
    }
}
