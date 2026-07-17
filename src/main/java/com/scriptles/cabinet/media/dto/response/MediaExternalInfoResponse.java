package com.scriptles.cabinet.media.dto.response;

import com.scriptles.cabinet.media.enums.ExternalInfoSectionState;
import com.scriptles.cabinet.media.enums.ExternalOfferType;
import com.scriptles.cabinet.media.enums.ExternalRatingMetric;
import com.scriptles.cabinet.media.enums.ExternalSource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MediaExternalInfoResponse(
        UUID mediaId,
        String countryCode,
        AvailabilitySection availability,
        RatingSection ratings
) {
    public boolean pendingWithoutData() {
        return availability.state() == ExternalInfoSectionState.PENDING
                || ratings.state() == ExternalInfoSectionState.PENDING;
    }

    public record AvailabilitySection(
            ExternalInfoSectionState state,
            Instant fetchedAt,
            Instant expiresAt,
            List<String> attributions,
            List<Offer> offers
    ) {
    }

    public record Offer(
            ExternalSource dataSource,
            String providerId,
            String providerName,
            String logoUrl,
            ExternalOfferType type,
            String url,
            String sourceUrl
    ) {
    }

    public record RatingSection(
            ExternalInfoSectionState state,
            Instant fetchedAt,
            Instant expiresAt,
            List<Rating> items
    ) {
    }

    public record Rating(
            ExternalSource provider,
            ExternalSource source,
            ExternalRatingMetric metric,
            Double value,
            Integer scale,
            String displayValue,
            String externalId
    ) {
    }
}
