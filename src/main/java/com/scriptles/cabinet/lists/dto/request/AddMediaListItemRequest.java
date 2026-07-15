package com.scriptles.cabinet.lists.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record AddMediaListItemRequest(
        @NotNull(message = "Informe a mídia que será adicionada")
        UUID mediaId,

        @Size(max = 2000, message = "As observações devem ter no máximo 2000 caracteres")
        String notes
) {
}
