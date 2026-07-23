package com.scriptles.cabinet.media.dto.response;

import java.util.List;

public record MediaCommunityResponse(
        long likeCount,
        List<MediaCommunityUserResponse> recentLikers,
        Double averageRating,
        List<ExternalMediaDetailsResponse.RatingDistributionBucket> ratingDistribution,
        long listCount,
        long completedCount,
        List<MediaCommunityUserResponse> recentCompleters
) {
}
