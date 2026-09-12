package com.scriptles.cabinet.user.dto.request;

import com.scriptles.cabinet.user.enums.Visibility;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

public record UpdateDiaryEntryRequest(
        @NotNull @PastOrPresent LocalDate occurredOn,
        boolean reconsumption,
        @DecimalMin("0.5") @DecimalMax("5.0") BigDecimal rating,
        @Size(max = 20_000) String review,
        Boolean containsSpoilers,
        @NotNull Visibility visibility,
        @Size(max = 30) Set<@Size(max = 100) String> tags
) {
    public UpdateDiaryEntryRequest {
        tags = tags == null ? Set.of() : tags;
    }
}
