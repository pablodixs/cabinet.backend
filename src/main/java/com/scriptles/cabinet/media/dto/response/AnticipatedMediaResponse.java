package com.scriptles.cabinet.media.dto.response;

import java.util.List;

public record AnticipatedMediaResponse(
        List<AnticipatedMediaItemResponse> items
) {
}
