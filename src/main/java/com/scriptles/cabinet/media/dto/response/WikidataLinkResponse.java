package com.scriptles.cabinet.media.dto.response;

import java.util.UUID;

public record WikidataLinkResponse(
        UUID mediaId,
        String wikidataId,
        String externalUrl,
        boolean enriched
) {
}
