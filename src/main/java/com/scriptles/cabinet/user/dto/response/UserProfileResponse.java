package com.scriptles.cabinet.user.dto.response;

import java.util.List;
import java.util.UUID;
import com.scriptles.cabinet.user.enums.FollowState;

public record UserProfileResponse(
        UUID id,
        String username,
        String displayName,
        String biography,
        String avatarUrl,
        boolean pro,
        boolean ownProfile,
        long libraryCount,
        long completedCount,
        long inProgressCount,
        ProfileStatsResponse stats,
        List<LibraryMediaResponse> recentItems,
        long followerCount,
        long followingCount,
        boolean privateProfile,
        FollowState followState,
        boolean followsViewer
) {
    public UserProfileResponse(
            UUID id,
            String username,
            String displayName,
            String biography,
            String avatarUrl,
            String ignoredEmail,
            boolean ownProfile,
            long libraryCount,
            long completedCount,
            long inProgressCount,
            ProfileStatsResponse stats,
            List<LibraryMediaResponse> recentItems
    ) {
        this(id, username, displayName, biography, avatarUrl, false, ownProfile,
                libraryCount, completedCount, inProgressCount, stats, recentItems);
    }

    public UserProfileResponse(
            UUID id,
            String username,
            String displayName,
            String biography,
            String avatarUrl,
            boolean pro,
            boolean ownProfile,
            long libraryCount,
            long completedCount,
            long inProgressCount,
            ProfileStatsResponse stats,
            List<LibraryMediaResponse> recentItems
    ) {
        this(id, username, displayName, biography, avatarUrl, pro, ownProfile,
                libraryCount, completedCount, inProgressCount, stats, recentItems,
                0, 0, false, FollowState.NONE, false);
    }
}
