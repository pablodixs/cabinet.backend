package com.scriptles.cabinet.user.dto.response;

import com.scriptles.cabinet.user.enums.FollowState;

import java.time.Instant;
import java.util.UUID;

public record SocialUserResponse(
        UUID id,
        String username,
        String displayName,
        String avatarUrl,
        boolean privateProfile,
        FollowState followState,
        boolean followsViewer,
        Instant relationshipAt
) {
}
