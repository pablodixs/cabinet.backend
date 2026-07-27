package com.scriptles.cabinet.user.dto.response;

import java.util.List;

public record ProfileRatingSummaryResponse(
        long total,
        List<ProfileRatingBucketResponse> distribution
) {
    public static ProfileRatingSummaryResponse empty() {
        return new ProfileRatingSummaryResponse(0, List.of());
    }
}
