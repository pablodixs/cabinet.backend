package com.scriptles.cabinet.lists.dto.response;

import java.util.List;
import java.util.UUID;

public record MediaListBackdropOptionsResponse(
        UUID listId,
        UUID selectedMediaId,
        String selectedKey,
        List<MediaListBackdropOptionResponse> options
) {
}
