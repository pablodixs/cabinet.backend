package com.scriptles.cabinet.media.dto.response;

import java.util.List;

public record TrendingMediaResponse(
        List<MediaSearchItemResponse> items,
        int periodDays
) {
}
