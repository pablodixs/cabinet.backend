package com.scriptles.cabinet.media.dto.response;

import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaRelationType;
import com.scriptles.cabinet.media.enums.MediaType;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record RelatedMediaResponse(
        ExternalSource source,
        boolean incomplete,
        List<Item> items
) {
    public record Item(
            UUID id,
            MediaRelationType relationType,
            MediaType type,
            String title,
            LocalDate releaseDate,
            String coverUrl,
            String wikidataId,
            ExternalSource providerSource,
            String providerExternalId,
            String externalUrl,
            boolean imported
    ) {
    }
}
