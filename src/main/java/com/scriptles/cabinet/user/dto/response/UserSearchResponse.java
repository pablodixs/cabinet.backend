package com.scriptles.cabinet.user.dto.response;

import com.scriptles.cabinet.user.entity.User;

import java.util.UUID;

public record UserSearchResponse(
        UUID id,
        String username,
        String displayName,
        String biography,
        String avatarUrl
) {
    public static UserSearchResponse from(User user) {
        return new UserSearchResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getBiography(),
                user.getAvatarUlr()
        );
    }
}
