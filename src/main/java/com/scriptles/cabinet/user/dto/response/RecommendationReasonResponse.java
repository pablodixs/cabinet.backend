package com.scriptles.cabinet.user.dto.response;

import com.scriptles.cabinet.user.enums.RecommendationReasonType;

public record RecommendationReasonResponse(
        RecommendationReasonType targetType,
        String targetId,
        String label
) {
}
