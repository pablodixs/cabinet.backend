package com.scriptles.cabinet.user.dto.response;

import com.scriptles.cabinet.media.dto.response.MediaSearchItemResponse;
import com.scriptles.cabinet.user.enums.RecommendationSource;

import java.util.List;

public record RecommendationItemResponse(
        MediaSearchItemResponse media,
        RecommendationSource source,
        List<RecommendationReasonResponse> reasons
) {
}
