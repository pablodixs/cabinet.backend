package com.scriptles.cabinet.user.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record UpdateMediaTagsRequest(
        @Size(max = 30) Set<@Valid @NotBlank @Size(max = 100) String> tags
) {
    public UpdateMediaTagsRequest {
        tags = tags == null ? Set.of() : tags;
    }
}
