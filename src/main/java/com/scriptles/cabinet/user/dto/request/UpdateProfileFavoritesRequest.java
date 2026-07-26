package com.scriptles.cabinet.user.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record UpdateProfileFavoritesRequest(
        @NotNull
        @Size(max = 4, message = "Você pode selecionar no máximo quatro obras favoritas")
        List<@NotNull UUID> mediaIds
) {
}
