package com.scriptles.cabinet.media.dto.response;

import com.scriptles.cabinet.media.external.ArtworkAsset;

public record ArtworkOptionResponse(
        String key,
        String url,
        String previewUrl,
        Integer width,
        Integer height,
        String language,
        String label
) {
    public static ArtworkOptionResponse from(ArtworkAsset asset) {
        return new ArtworkOptionResponse(
                asset.key(), asset.url(), asset.previewUrl(), asset.width(), asset.height(), asset.language(), asset.label()
        );
    }
}
