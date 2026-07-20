package com.scriptles.cabinet.user.dto.response;

import java.util.List;
import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String username,
        String displayName,
        String biography,
        String avatarUrl,
        String email,
        boolean ownProfile,
        long libraryCount,
        long completedCount,
        long inProgressCount,
        ProfileStatsResponse stats,
        List<LibraryMediaResponse> recentItems
) {
}
