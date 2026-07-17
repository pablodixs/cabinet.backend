package com.scriptles.cabinet.media.external;

import com.scriptles.cabinet.media.enums.ExternalOfferType;
import com.scriptles.cabinet.media.enums.ExternalSource;

import java.util.List;

public record ExternalAvailability(
        ExternalSource source,
        String attribution,
        String sourceUrl,
        List<Offer> offers
) {
    public ExternalAvailability {
        offers = offers == null ? List.of() : List.copyOf(offers);
    }

    public record Offer(
            String providerId,
            String providerName,
            String logoUrl,
            ExternalOfferType type,
            String url,
            Integer displayPriority
    ) {
    }
}
