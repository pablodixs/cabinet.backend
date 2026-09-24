package com.scriptles.cabinet.user.dto.request;

import com.scriptles.cabinet.user.enums.Visibility;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public record CreateDiaryEntryRequest(
        @NotNull UUID mediaId,
        @NotNull @PastOrPresent LocalDate occurredOn,
        boolean reconsumption,
        @DecimalMin("0.5") @DecimalMax("5.0") BigDecimal rating,
        @Size(max = 20_000) String review,
        Boolean containsSpoilers,
        @NotNull Visibility visibility,
        @Size(max = 30) Set<@Valid @NotBlank @Size(max = 100) String> tags,
        @Size(max = 500) String backdropKey,
        JsonNode richContent
) {
    public CreateDiaryEntryRequest(
            UUID mediaId,
            LocalDate occurredOn,
            boolean reconsumption,
            BigDecimal rating,
            String review,
            Boolean containsSpoilers,
            Visibility visibility,
            Set<@Valid @NotBlank @Size(max = 100) String> tags
    ) {
        this(mediaId, occurredOn, reconsumption, rating, review, containsSpoilers,
                visibility, tags, null, null);
    }
    public CreateDiaryEntryRequest(UUID mediaId, LocalDate occurredOn, boolean reconsumption, BigDecimal rating, String review, Boolean containsSpoilers, Visibility visibility, Set<@Valid @NotBlank @Size(max = 100) String> tags, String backdropKey) { this(mediaId,occurredOn,reconsumption,rating,review,containsSpoilers,visibility,tags,backdropKey,null); }
}
