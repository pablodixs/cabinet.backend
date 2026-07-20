package com.scriptles.cabinet.user.dto.response;

import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.enums.AccountTier;
import com.scriptles.cabinet.user.enums.UserRole;

import java.time.Instant;
import java.util.UUID;

public record CommunityUserRoleResponse(
        UUID id,
        String username,
        String displayName,
        String email,
        UserRole role,
        AccountTier accountTier,
        boolean pro,
        boolean active,
        Instant memberSince
) {
    public static CommunityUserRoleResponse from(User user, UserRole effectiveRole) {
        return new CommunityUserRoleResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getEmail(),
                effectiveRole,
                user.getAccountTier() == null ? AccountTier.FREE : user.getAccountTier(),
                user.getAccountTier() == AccountTier.PRO,
                Boolean.TRUE.equals(user.getActive()),
                user.getCreatedAt()
        );
    }
}
