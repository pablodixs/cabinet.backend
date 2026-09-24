package com.scriptles.cabinet.lists.dto.request;

import com.scriptles.cabinet.user.enums.Visibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;
import java.util.Set;
import tools.jackson.databind.JsonNode;

public record UpdateMediaListRequest(
        @NotBlank(message = "Informe um nome para a lista")
        @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres")
        String name,

        @Size(max = 2000, message = "A descrição deve ter no máximo 2000 caracteres")
        String description,

        @NotNull(message = "Informe a visibilidade da lista")
        Visibility visibility,

        @NotNull(message = "Informe se a lista é ordenada")
        Boolean ordered,

        @Size(max = 500, message = "A URL da capa deve ter no máximo 500 caracteres")
        String coverUrl,

        UUID backdropMediaId,

        @Size(max = 500, message = "A chave do backdrop deve ter no máximo 500 caracteres")
        String backdropKey,

        @Size(max = 30)
        Set<@NotBlank @Size(max = 100) String> tags,
        JsonNode richDescription
) {
    public UpdateMediaListRequest(
            String name,
            String description,
            Visibility visibility,
            Boolean ordered,
            String coverUrl
    ) {
        this(name, description, visibility, ordered, coverUrl, null, null,
                Set.of(), null);
    }

    public UpdateMediaListRequest(
            String name,
            String description,
            Visibility visibility,
            Boolean ordered,
            String coverUrl,
            UUID backdropMediaId,
            String backdropKey
    ) {
        this(name, description, visibility, ordered, coverUrl,
                backdropMediaId, backdropKey, Set.of(), null);
    }

    public UpdateMediaListRequest(String name, String description, Visibility visibility, Boolean ordered, String coverUrl, UUID backdropMediaId, String backdropKey, Set<@NotBlank @Size(max = 100) String> tags) { this(name,description,visibility,ordered,coverUrl,backdropMediaId,backdropKey,tags,null); }

    public UpdateMediaListRequest {
        tags = tags == null ? Set.of() : tags;
    }
}
