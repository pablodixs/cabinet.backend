package com.scriptles.cabinet.user.dto.response;

import com.scriptles.cabinet.user.enums.FollowState;

import java.util.UUID;

public record FollowActionResponse(
        UUID targetUserId,
        FollowState state
) {
}
