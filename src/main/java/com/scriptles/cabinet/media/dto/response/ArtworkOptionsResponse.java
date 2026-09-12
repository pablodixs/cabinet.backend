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
        String selectedCoverUrl,
        String selectedBackdropUrl,
        List<ArtworkOptionResponse> coverOptions,
        List<ArtworkOptionResponse> backdropOptions
) {
    /**
     * Backwards-compatible constructor for callers that only need the keys.
     * The selected URLs are resolved by the artwork service when available.
     */
    public ArtworkOptionsResponse(
            UUID mediaId,
            ArtworkProvider provider,
            String defaultCoverUrl,
            String defaultBackdropUrl,
            String selectedCoverKey,
            String selectedBackdropKey,
            List<ArtworkOptionResponse> coverOptions,
            List<ArtworkOptionResponse> backdropOptions
    ) {
        this(mediaId, provider, defaultCoverUrl, defaultBackdropUrl,
                selectedCoverKey, selectedBackdropKey, null, null,
                coverOptions, backdropOptions);
    }
}
