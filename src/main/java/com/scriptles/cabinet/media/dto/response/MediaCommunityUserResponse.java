package com.scriptles.cabinet.media.dto.response;

import java.util.UUID;

public record MediaCommunityUserResponse(
        UUID id,
        String username,
        String avatarUrl
) {
}
