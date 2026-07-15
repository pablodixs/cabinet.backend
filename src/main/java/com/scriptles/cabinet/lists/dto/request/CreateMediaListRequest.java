package com.scriptles.cabinet.lists.dto.request;

import com.scriptles.cabinet.user.enums.Visibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateMediaListRequest(
        @NotBlank(message = "Informe um nome para a lista")
        @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres")
        String name,

        @Size(max = 2000, message = "A descrição deve ter no máximo 2000 caracteres")
        String description,

        Visibility visibility,

        Boolean ordered,

        @Size(max = 500, message = "A URL da capa deve ter no máximo 500 caracteres")
        String coverUrl
) {
}
