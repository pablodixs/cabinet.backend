package com.scriptles.cabinet.media.dto.response;

import com.scriptles.cabinet.media.enums.MediaType;

import java.util.List;

public record MediaCommunityResponse(
        long likeCount,
        List<MediaCommunityUserResponse> recentLikers,
        Double averageRating,
        List<ExternalMediaDetailsResponse.RatingDistributionBucket> ratingDistribution,
        ChildRatingsResponse childRatings,
        long listCount,
        long completedCount,
        List<MediaCommunityUserResponse> recentCompleters
) {
    public record ChildRatingsResponse(
            MediaType itemType,
            Double averageRating,
            long ratingCount,
            List<ExternalMediaDetailsResponse.RatingDistributionBucket> ratingDistribution
    ) {
    }
}
