package com.scriptles.cabinet.user.dto.response;

import java.util.List;

public record RecommendationResponse(
        List<RecommendationItemResponse> items
) {
}
