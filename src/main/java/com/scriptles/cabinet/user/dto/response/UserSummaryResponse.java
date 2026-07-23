package com.scriptles.cabinet.user.dto.response;

import com.scriptles.cabinet.user.enums.FollowState;

import java.util.UUID;

public record UserSummaryResponse(
        UUID id,
        String username,
        String displayName,
        String biography,
        String avatarUrl,
        boolean pro,
        boolean ownProfile,
        boolean privateProfile,
        boolean contentAccessible,
        long followerCount,
        long followingCount,
        FollowState followState,
        boolean followsViewer
) {
}
