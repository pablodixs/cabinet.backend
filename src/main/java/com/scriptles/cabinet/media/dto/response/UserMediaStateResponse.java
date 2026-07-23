package com.scriptles.cabinet.media.dto.response;

import com.scriptles.cabinet.user.enums.UserMediaStatus;

import java.util.List;
import java.util.UUID;

public record UserMediaStateResponse(
        boolean liked,
        UserMediaStatus status,
        Double rating,
        UUID reviewId,
        List<UUID> listIds,
        String customCoverUrl,
        String customBackdropUrl
) {
}
