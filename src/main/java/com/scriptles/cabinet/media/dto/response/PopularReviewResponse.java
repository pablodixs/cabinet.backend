package com.scriptles.cabinet.media.dto.response;

public record PopularReviewResponse(
        ReviewResponse review,
        MediaSearchItemResponse media
) {
}
