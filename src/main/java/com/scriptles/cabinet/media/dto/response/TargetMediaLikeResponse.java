package com.scriptles.cabinet.media.dto.response;

import com.scriptles.cabinet.media.enums.CatalogStatus;

import java.util.UUID;

public record TargetMediaLikeResponse(
        UUID mediaId,
        boolean liked,
        CatalogStatus catalogStatus,
        boolean enrichmentPending
) {
}
