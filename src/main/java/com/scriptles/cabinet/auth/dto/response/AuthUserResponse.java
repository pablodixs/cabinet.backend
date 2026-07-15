package com.scriptles.cabinet.auth.dto.response;

import com.scriptles.cabinet.security.AuthenticatedUser;

import java.util.UUID;

public record AuthUserResponse(
        UUID id,
        String username,
        String displayName,
        String email
) {
    public static AuthUserResponse from(AuthenticatedUser user) {
        return new AuthUserResponse(
                user.id(),
                user.username(),
                user.displayName(),
                user.email()
        );
    }
}
