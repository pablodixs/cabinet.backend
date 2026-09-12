package com.scriptles.cabinet.user.dto.response;

import java.util.List;
import java.util.UUID;
import com.scriptles.cabinet.user.enums.FollowState;
import com.scriptles.cabinet.user.enums.Visibility;

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
        List<ProfileFavoriteResponse> favoriteItems,
        long followerCount,
        long followingCount,
        boolean privateProfile,
        Visibility profileVisibility,
        FollowState followState,
        boolean followsViewer,
        ProfileRatingSummaryResponse ratings
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
                libraryCount, completedCount, inProgressCount, stats, recentItems, List.of(),
                0, 0, false, Visibility.PUBLIC, FollowState.NONE, false,
                ProfileRatingSummaryResponse.empty());
    }
}
