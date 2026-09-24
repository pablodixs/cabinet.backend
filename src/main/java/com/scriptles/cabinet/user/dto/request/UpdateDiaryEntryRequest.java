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
import tools.jackson.databind.JsonNode;

public record UpdateDiaryEntryRequest(
        @NotNull @PastOrPresent LocalDate occurredOn,
        boolean reconsumption,
        @DecimalMin("0.5") @DecimalMax("5.0") BigDecimal rating,
        @Size(max = 20_000) String review,
        Boolean containsSpoilers,
        @NotNull Visibility visibility,
        @Size(max = 30) Set<@Size(max = 100) String> tags,
        @Size(max = 500) String backdropKey,
        JsonNode richContent
) {
    public UpdateDiaryEntryRequest {
        tags = tags == null ? Set.of() : tags;
    }

    public UpdateDiaryEntryRequest(
            LocalDate occurredOn,
            boolean reconsumption,
            BigDecimal rating,
            String review,
            Boolean containsSpoilers,
            Visibility visibility,
            Set<@Size(max = 100) String> tags
    ) {
        this(occurredOn, reconsumption, rating, review, containsSpoilers,
                visibility, tags, null, null);
    }
    public UpdateDiaryEntryRequest(LocalDate occurredOn, boolean reconsumption, BigDecimal rating, String review, Boolean containsSpoilers, Visibility visibility, Set<@Size(max = 100) String> tags, String backdropKey) { this(occurredOn,reconsumption,rating,review,containsSpoilers,visibility,tags,backdropKey,null); }
}
