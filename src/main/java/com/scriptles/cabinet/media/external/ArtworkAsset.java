package com.scriptles.cabinet.media.external;

public record ArtworkAsset(
        String key,
        String url,
        String previewUrl,
        Integer width,
        Integer height,
        String language
) {
}
