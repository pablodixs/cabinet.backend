package com.scriptles.cabinet.media.dto.request;

import com.scriptles.cabinet.user.enums.Visibility;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public record UpsertReviewRequest(
        @DecimalMin("0.5") @DecimalMax("5.0") BigDecimal rating,
        @NotBlank @Size(max = 20_000) String content,
        Boolean containsSpoilers,
        Visibility visibility,
        UUID activityId,
        @Size(max = 500) String backdropKey,
        JsonNode richContent
) {
    public UpsertReviewRequest(
            BigDecimal rating,
            String content,
            Boolean containsSpoilers,
            Visibility visibility,
            UUID activityId
    ) {
        this(rating, content, containsSpoilers, visibility, activityId, null, null);
    }

    public UpsertReviewRequest(
            BigDecimal rating,
            String content,
            Boolean containsSpoilers,
            Visibility visibility
    ) {
        this(rating, content, containsSpoilers, visibility, null, null, null);
    }
}
