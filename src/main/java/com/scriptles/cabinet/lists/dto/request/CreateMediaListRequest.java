package com.scriptles.cabinet.lists.dto.request;

import com.scriptles.cabinet.user.enums.Visibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;
import tools.jackson.databind.JsonNode;

public record CreateMediaListRequest(
        @NotBlank(message = "Informe um nome para a lista")
        @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres")
        String name,

        @Size(max = 2000, message = "A descrição deve ter no máximo 2000 caracteres")
        String description,

        Visibility visibility,

        Boolean ordered,

        @Size(max = 500, message = "A URL da capa deve ter no máximo 500 caracteres")
        String coverUrl,

        @Size(max = 30)
        Set<@NotBlank @Size(max = 100) String> tags,
        JsonNode richDescription
) {
    public CreateMediaListRequest(
            String name,
            String description,
            Visibility visibility,
            Boolean ordered,
            String coverUrl
    ) {
        this(name, description, visibility, ordered, coverUrl, Set.of(), null);
    }

    public CreateMediaListRequest(String name, String description, Visibility visibility, Boolean ordered, String coverUrl, Set<@NotBlank @Size(max = 100) String> tags) { this(name,description,visibility,ordered,coverUrl,tags,null); }

    public CreateMediaListRequest {
        tags = tags == null ? Set.of() : tags;
    }
}
