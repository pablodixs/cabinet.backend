package com.scriptles.cabinet.user.dto.response;

import com.scriptles.cabinet.media.entity.MediaLike;
import com.scriptles.cabinet.media.enums.MediaType;

import java.time.Instant;
import java.util.UUID;

public record ProfileLikeResponse(
        UUID mediaId,
        MediaType type,
        String title,
        String coverUrl,
        Instant likedAt
) {
    public static ProfileLikeResponse from(MediaLike like, String coverUrl) {
        return new ProfileLikeResponse(
                like.getMedia().getId(),
                like.getMedia().getType(),
                like.getMedia().getTitle(),
                coverUrl,
                like.getLikedAt()
        );
    }
}
