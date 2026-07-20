package com.scriptles.cabinet.user.dto.response;

import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.user.entity.UserMediaActivity;
import com.scriptles.cabinet.user.enums.ProfileActivityType;
import com.scriptles.cabinet.user.enums.Visibility;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DiaryEntryResponse(
        UUID id,
        ProfileActivityType type,
        LocalDate occurredOn,
        LocalDate loggedOn,
        UUID mediaId,
        MediaType mediaType,
        String title,
        String coverUrl,
        LocalDate releaseDate,
        ExternalSource source,
        String externalId,
        BigDecimal rating,
        String review,
        boolean containsSpoilers,
        Visibility visibility,
        List<String> tags
) {
    public static DiaryEntryResponse from(
            UserMediaActivity activity,
            ExternalReference externalReference
    ) {
        Media media = activity.getMedia();
        return new DiaryEntryResponse(
                activity.getId(),
                activity.getType(),
                activity.getOccurredOn(),
                activity.getLoggedOn(),
                media.getId(),
                media.getType(),
                media.getTitle(),
                media.getCoverUrl(),
                media.getReleaseDate(),
                externalReference == null ? null : externalReference.getSource(),
                externalReference == null ? null : externalReference.getExternalId(),
                activity.getRating(),
                activity.getReviewContent(),
                Boolean.TRUE.equals(activity.getContainsSpoilers()),
                activity.getVisibility(),
                List.copyOf(activity.getTags())
        );
    }
}
