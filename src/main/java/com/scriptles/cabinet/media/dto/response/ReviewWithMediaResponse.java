package com.scriptles.cabinet.media.dto.response;

public record ReviewWithMediaResponse(
        ReviewResponse review,
        MediaSearchItemResponse media
) {}
