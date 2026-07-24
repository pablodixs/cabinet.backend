package com.scriptles.cabinet.media.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpsertExternalRatingRequest(
        @NotNull @Valid MediaTarget media,
        @NotNull @DecimalMin("0.5") @DecimalMax("5.0") BigDecimal rating
) {
}
