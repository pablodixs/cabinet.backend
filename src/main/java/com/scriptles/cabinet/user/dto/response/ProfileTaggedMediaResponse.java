package com.scriptles.cabinet.user.dto.response;

import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.MediaType;

import java.util.UUID;

public record ProfileTaggedMediaResponse(
        UUID mediaId,
        MediaType type,
        String title,
        String coverUrl
) {
    public static ProfileTaggedMediaResponse from(
            Media media, String coverUrl) {
        return new ProfileTaggedMediaResponse(
                media.getId(),
                media.getType(),
                media.getTitle(),
                coverUrl
        );
    }
}
