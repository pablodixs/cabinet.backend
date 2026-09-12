package com.scriptles.cabinet.user.dto.request;

import com.scriptles.cabinet.user.enums.Visibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateUserProfile(
        @NotBlank(message = "Display name is required")
        @Size(max = 80, message = "Display name must be at most 80 characters")
        String displayName,
        @Size(max = 500, message = "Biography must be at most 500 characters")
        String biography,
        @Size(max = 2000, message = "Avatar URL must be at most 2000 characters")
        String avatarUrl,
        @NotNull(message = "Profile visibility is required")
        Visibility profileVisibility
) {
}
