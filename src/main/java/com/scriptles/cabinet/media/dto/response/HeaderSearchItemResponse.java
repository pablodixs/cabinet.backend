package com.scriptles.cabinet.media.dto.response;

import com.scriptles.cabinet.media.enums.HeaderSearchEntityType;

import java.util.UUID;

public record HeaderSearchItemResponse(
        UUID id,
        HeaderSearchEntityType entityType,
        String title,
        String creator,
        String coverUrl,
        Integer year
) {
}
