package com.scriptles.cabinet.auth.dto.response;

import com.scriptles.cabinet.security.AuthenticatedUser;
import com.scriptles.cabinet.user.enums.AccountTier;
import com.scriptles.cabinet.user.enums.UserRole;

import java.util.UUID;

public record AuthUserResponse(
        UUID id,
        String username,
        String displayName,
        String email,
        UserRole role,
        boolean moderator,
        boolean admin,
        AccountTier accountTier,
        boolean pro
) {
    public static AuthUserResponse from(AuthenticatedUser user) {
        return new AuthUserResponse(
                user.id(),
                user.username(),
                user.displayName(),
                user.email(),
                user.role(),
                user.moderator(),
                user.admin(),
                user.accountTier(),
                user.pro()
        );
    }
}
