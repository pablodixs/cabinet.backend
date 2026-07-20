package com.scriptles.cabinet.media.dto.response;

import com.scriptles.cabinet.media.enums.ArtworkProvider;

import java.util.List;
import java.util.UUID;

public record ArtworkOptionsResponse(
        UUID mediaId,
        ArtworkProvider provider,
        String defaultCoverUrl,
        String defaultBackdropUrl,
        String selectedCoverKey,
        String selectedBackdropKey,
        List<ArtworkOptionResponse> coverOptions,
        List<ArtworkOptionResponse> backdropOptions
) {
}
