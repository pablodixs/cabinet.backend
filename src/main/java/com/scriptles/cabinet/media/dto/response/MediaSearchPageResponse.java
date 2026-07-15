package com.scriptles.cabinet.media.dto.response;

import java.util.List;

public record MediaSearchPageResponse(
        List<MediaSearchItemResponse> items,
        String nextCursor
) {
}
