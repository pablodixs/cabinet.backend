package com.scriptles.cabinet.media.external;

import com.scriptles.cabinet.media.enums.ArtworkProvider;

import java.util.List;

public record ArtworkCatalog(
        ArtworkProvider provider,
        List<ArtworkAsset> covers,
        List<ArtworkAsset> backdrops
) {
}
