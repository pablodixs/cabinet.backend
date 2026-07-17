package com.scriptles.cabinet.media.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;

public record UpdateMediaMetadataRequest(
        @NotNull Long version,
        @NotBlank @Size(max = 300) String title,
        @Size(max = 300) String originalTitle,
        @Size(max = 20000) String description,
        @Size(max = 500) String tagline,
        @Size(max = 5000) String coverUrl,
        @Size(max = 5000) String backdropUrl,
        @Size(max = 5000) String logoUrl,
        LocalDate releaseDate,
        @Pattern(regexp = "^[a-zA-Z]{2,3}(-[a-zA-Z]{2})?$") String originalLanguage,
        @Pattern(regexp = "^[a-zA-Z]{2,3}$") String countryCode,
        @Size(max = 30) List<@NotBlank @Size(max = 100) String> genres
) {
}
