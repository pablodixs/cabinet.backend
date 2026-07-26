package com.scriptles.cabinet.media.translation;

import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.TranslationStatus;

import java.util.UUID;

public record ResolvedMediaTranslation(
        UUID mediaId,
        String title,
        String description,
        String tagline,
        String requestedLocale,
        String resolvedLocale,
        boolean fallback,
        boolean partialFallback,
        TranslationStatus status,
        ExternalSource source
) {
}
