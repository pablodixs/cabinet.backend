package com.scriptles.cabinet.media.dto.request;

import com.scriptles.cabinet.user.enums.Visibility;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record UpsertReviewRequest(
        @DecimalMin("0.5") @DecimalMax("5.0") BigDecimal rating,
        @NotBlank @Size(max = 20_000) String content,
        Boolean containsSpoilers,
        Visibility visibility,
        UUID activityId
) {
    public UpsertReviewRequest(
            BigDecimal rating,
            String content,
            Boolean containsSpoilers,
            Visibility visibility
    ) {
        this(rating, content, containsSpoilers, visibility, null);
    }
}
