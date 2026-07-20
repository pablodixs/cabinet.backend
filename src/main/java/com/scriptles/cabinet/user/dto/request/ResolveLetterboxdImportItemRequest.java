package com.scriptles.cabinet.user.dto.request;

import java.util.UUID;

public record ResolveLetterboxdImportItemRequest(
        UUID mediaId,
        String tmdbId,
        Boolean ignored,
        Boolean overrideStatus,
        Boolean overrideRating,
        Boolean overrideReview
) {
}
