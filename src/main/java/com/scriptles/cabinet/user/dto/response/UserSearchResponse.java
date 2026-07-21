package com.scriptles.cabinet.user.dto.response;

import com.scriptles.cabinet.user.entity.User;

import java.util.UUID;
import com.scriptles.cabinet.user.enums.FollowState;

public record UserSearchResponse(
        UUID id,
        String username,
        String displayName,
        String biography,
        String avatarUrl,
        boolean privateProfile,
        boolean contentAccessible,
        FollowState followState,
        boolean followsViewer
) {
    public UserSearchResponse(
            UUID id, String username, String displayName, String biography, String avatarUrl) {
        this(id, username, displayName, biography, avatarUrl,
                false, true, FollowState.NONE, false);
    }

    public static UserSearchResponse from(User user) {
        return new UserSearchResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getBiography(),
                user.getAvatarUlr(),
                false,
                true,
                FollowState.NONE,
                false
        );
    }
}
