package com.scriptles.cabinet.user.dto.request;

import jakarta.validation.constraints.Size;

public record UpdateUserProfile(
        @Size(max = 80, message = "Display name must be at most 80 characters")
        String displayName,
        @Size(max = 500, message = "Biography must be at most 200 characters")
        String biography,
        String avatarUrl
) {
}
