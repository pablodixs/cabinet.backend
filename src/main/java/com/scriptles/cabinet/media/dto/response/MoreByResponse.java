package com.scriptles.cabinet.media.dto.response;

import com.scriptles.cabinet.media.enums.CreditRole;
import com.scriptles.cabinet.media.enums.MoreByState;

import java.util.List;
import java.util.UUID;

public record MoreByResponse(
        MoreByState state,
        CreditRole role,
        PersonResponse person,
        boolean incomplete,
        List<MediaSearchItemResponse> items
) {
    public record PersonResponse(
            UUID id,
            String name,
            String imageUrl
    ) {
    }
}
