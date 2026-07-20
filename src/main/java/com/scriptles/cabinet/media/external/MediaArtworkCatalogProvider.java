package com.scriptles.cabinet.media.external;

import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;

public interface MediaArtworkCatalogProvider {
    boolean supports(ExternalSource source, MediaType type);

    ArtworkCatalog find(MediaType type, String externalId, String language);
}
