package com.scriptles.cabinet.media.dto.request;

import com.scriptles.cabinet.user.enums.Visibility;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpsertReviewRequest(
        @NotNull @DecimalMin("0.5") @DecimalMax("5.0") BigDecimal rating,
        String content,
        @NotNull Boolean containsSpoilers,
        @NotNull Visibility visibility
) {
}
