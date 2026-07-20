package com.scriptles.cabinet.media.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpsertRatingRequest(
        @NotNull @DecimalMin("0.5") @DecimalMax("5.0") BigDecimal rating
) {}
