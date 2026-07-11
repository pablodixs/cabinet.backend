package com.scriptles.cabinet.media.external;

import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ExternalMediaProviderRegistry {
    private final List<ExternalMediaProvider> providers;

    public ExternalMediaProvider get(ExternalSource source, MediaType mediaType) {
        return providers.stream()
                .filter(provider -> provider.source() == source && provider.supports(mediaType))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Fonte %s nao suporta o tipo %s".formatted(source, mediaType)
                ));
    }
}
