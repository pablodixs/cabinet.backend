package com.scriptles.cabinet.media.dto.request;

import jakarta.validation.constraints.Size;

public record UpsertUserMediaArtworkRequest(
        @Size(max = 2000) String coverKey,
        @Size(max = 2000) String backdropKey
) {
}
