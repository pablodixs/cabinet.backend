package com.scriptles.cabinet.lists.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DuplicateMediaListRequest(
        @NotBlank(message = "Informe um nome para a lista")
        @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres")
        String name
) {
}
