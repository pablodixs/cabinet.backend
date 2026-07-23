package com.scriptles.cabinet.media.dto.response;

import java.util.UUID;

public record MediaCommunityUserResponse(
        UUID id,
        String username,
        String avatarUrl,
        boolean pro
) {
    public MediaCommunityUserResponse(UUID id, String username, String avatarUrl) {
        this(id, username, avatarUrl, false);
    }
}
