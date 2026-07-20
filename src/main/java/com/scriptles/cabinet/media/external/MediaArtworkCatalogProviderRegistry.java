package com.scriptles.cabinet.media.external;

import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class MediaArtworkCatalogProviderRegistry {
    private final List<MediaArtworkCatalogProvider> providers;

    public Optional<MediaArtworkCatalogProvider> find(ExternalSource source, MediaType type) {
        return providers.stream().filter(provider -> provider.supports(source, type)).findFirst();
    }
}
