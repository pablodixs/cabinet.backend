package com.scriptles.cabinet.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 30, message = "Username must be between 3 and 30 characters")
        @Pattern(
                regexp = "^[a-zA-Z0-9._]+$",
                message = "The username can only contain letters, numbers, dots, and underscores"
        )
        String username,

        @NotBlank(message = "Display name is required")
        @Size(min = 3, max = 80, message = "Display name must be between 3 and 80 characters")
        String displayName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email should be valid")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
        String password
        ) {
}
