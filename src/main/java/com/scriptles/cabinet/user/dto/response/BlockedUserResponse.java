package com.scriptles.cabinet.user.dto.response;

import java.time.Instant;
import java.util.UUID;

public record BlockedUserResponse(
        UUID id,
        String username,
        String displayName,
        String avatarUrl,
        Instant blockedAt
) {
}
