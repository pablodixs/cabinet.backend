package com.scriptles.cabinet.media.dto.response;

import java.util.List;

public record ReviewLikeResponse(
        boolean liked,
        long likeCount,
        List<ReviewLikerResponse> recentLikers
) {
}
