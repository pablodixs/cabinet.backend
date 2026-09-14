package com.scriptles.cabinet.media.dto.response;

import com.scriptles.cabinet.user.enums.UserMediaStatus;

import java.util.List;
import java.util.UUID;
import java.time.LocalDate;

public record UserMediaStateResponse(
        boolean liked,
        UserMediaStatus status,
        Double rating,
        UUID reviewId,
        List<UUID> listIds,
        String customCoverUrl,
        String customBackdropUrl,
        long listenCount,
        LocalDate lastListenedOn,
        boolean inRotation
) {
    public UserMediaStateResponse(
            boolean liked,
            UserMediaStatus status,
            Double rating,
            UUID reviewId,
            List<UUID> listIds,
            String customCoverUrl,
            String customBackdropUrl
    ) {
        this(liked, status, rating, reviewId, listIds, customCoverUrl, customBackdropUrl, 0, null, false);
    }
}
