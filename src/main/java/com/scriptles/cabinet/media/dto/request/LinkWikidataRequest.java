package com.scriptles.cabinet.media.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record LinkWikidataRequest(
        @NotBlank
        @Pattern(regexp = "^Q[1-9]\\d*$", message = "wikidataId must be a valid QID, for example Q190050")
        String wikidataId,
        @Pattern(regexp = "^[a-z]{2}(-[A-Z]{2})?$")
        String language
) {
    public String effectiveLanguage() {
        return language == null ? "pt-BR" : language;
    }
}
