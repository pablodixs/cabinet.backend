package com.scriptles.cabinet.lists.dto.response;

import com.scriptles.cabinet.media.enums.MediaType;

public record MediaListPreviewResponse(
        String coverUrl,
        MediaType type
) {
}
