package com.scriptles.cabinet.media.dto.response;

import java.util.UUID;

public record ReviewLikerResponse(
        UUID id,
        String username,
        String avatarUrl,
        boolean pro
) {
    public ReviewLikerResponse(UUID id, String username, String avatarUrl) {
        this(id, username, avatarUrl, false);
    }
}
