package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.media.dto.response.MediaExternalInfoResponse;
import com.scriptles.cabinet.media.entity.ExternalInfoSnapshot;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.MediaAvailabilityOffer;
import com.scriptles.cabinet.media.entity.MediaExternalRating;
import com.scriptles.cabinet.media.enums.ExternalInfoKind;
import com.scriptles.cabinet.media.enums.ExternalInfoSectionState;
import com.scriptles.cabinet.media.enums.ExternalInfoSnapshotStatus;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.repository.ExternalInfoSnapshotRepository;
import com.scriptles.cabinet.media.repository.MediaAvailabilityOfferRepository;
import com.scriptles.cabinet.media.repository.MediaExternalRatingRepository;
import com.scriptles.cabinet.media.repository.MediaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MediaExternalInfoService {
    public static final String GLOBAL_REGION = "GLOBAL";

    private final MediaRepository mediaRepository;
    private final ExternalInfoSnapshotRepository snapshotRepository;
    private final MediaAvailabilityOfferRepository availabilityOfferRepository;
    private final MediaExternalRatingRepository externalRatingRepository;
    private final ExternalInfoRefreshScheduler refreshScheduler;

    @Transactional(readOnly = true)
    public MediaExternalInfoResponse find(UUID mediaId, String requestedCountryCode) {
        Media media = mediaRepository.findById(mediaId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "MEDIA_NOT_FOUND",
                "Mídia não encontrada"
        ));
        String countryCode = requestedCountryCode.toUpperCase(Locale.ROOT);

        boolean availabilitySupported = supportsAvailability(media.getType());
        boolean ratingsSupported = supportsRatings(media.getType());
        ExternalInfoSnapshot availabilitySnapshot = availabilitySupported
                ? snapshotRepository.findByMediaIdAndKindAndRegion(
                        mediaId, ExternalInfoKind.AVAILABILITY, countryCode).orElse(null)
                : null;
        ExternalInfoSnapshot ratingSnapshot = ratingsSupported
                ? snapshotRepository.findByMediaIdAndKindAndRegion(
                        mediaId, ExternalInfoKind.RATINGS, GLOBAL_REGION).orElse(null)
                : null;

        List<MediaAvailabilityOffer> offers = availabilitySupported
                ? availabilityOfferRepository
                        .findAllByMediaIdAndCountryCodeOrderByDisplayPriorityAscProviderNameAsc(mediaId, countryCode)
                : List.of();
        List<MediaExternalRating> ratings = ratingsSupported
                ? externalRatingRepository.findAllByMediaIdOrderByMetricAsc(mediaId)
                : List.of();

        if (availabilitySupported && needsRefresh(availabilitySnapshot)) {
            refreshScheduler.scheduleAvailability(mediaId, countryCode);
        }
        if (ratingsSupported && needsRefresh(ratingSnapshot)) {
            refreshScheduler.scheduleRatings(mediaId);
        }

        return new MediaExternalInfoResponse(
                mediaId,
                countryCode,
                availabilitySection(availabilitySupported, availabilitySnapshot, offers),
                ratingSection(ratingsSupported, ratingSnapshot, ratings)
        );
    }

    private MediaExternalInfoResponse.AvailabilitySection availabilitySection(
            boolean supported,
            ExternalInfoSnapshot snapshot,
            List<MediaAvailabilityOffer> offers
    ) {
        if (!supported) {
            return new MediaExternalInfoResponse.AvailabilitySection(
                    ExternalInfoSectionState.NOT_SUPPORTED, null, null, List.of(), List.of());
        }
        List<MediaExternalInfoResponse.Offer> responseOffers = offers.stream()
                .map(offer -> new MediaExternalInfoResponse.Offer(
                        offer.getDataSource(),
                        offer.getProviderId(),
                        offer.getProviderName(),
                        offer.getLogoUrl(),
                        offer.getOfferType(),
                        offer.getExternalUrl(),
                        offer.getSourceUrl()
                ))
                .toList();
        List<String> attributions = offers.stream()
                .map(MediaAvailabilityOffer::getDataSource)
                .distinct()
                .map(this::attribution)
                .toList();
        return new MediaExternalInfoResponse.AvailabilitySection(
                state(snapshot, !offers.isEmpty()),
                fetchedAt(snapshot),
                expiresAt(snapshot),
                attributions,
                responseOffers
        );
    }

    private MediaExternalInfoResponse.RatingSection ratingSection(
            boolean supported,
            ExternalInfoSnapshot snapshot,
            List<MediaExternalRating> ratings
    ) {
        if (!supported) {
            return new MediaExternalInfoResponse.RatingSection(
                    ExternalInfoSectionState.NOT_SUPPORTED, null, null, List.of());
        }
        return new MediaExternalInfoResponse.RatingSection(
                state(snapshot, !ratings.isEmpty()),
                fetchedAt(snapshot),
                expiresAt(snapshot),
                ratings.stream().map(this::toRating).toList()
        );
    }

    private MediaExternalInfoResponse.Rating toRating(MediaExternalRating rating) {
        return new MediaExternalInfoResponse.Rating(
                rating.getProvider(),
                rating.getSource(),
                rating.getMetric(),
                rating.getValue(),
                rating.getScale(),
                rating.getDisplayValue(),
                rating.getExternalId()
        );
    }

    private ExternalInfoSectionState state(ExternalInfoSnapshot snapshot, boolean hasData) {
        if (snapshot == null) {
            return ExternalInfoSectionState.PENDING;
        }
        if (needsRefresh(snapshot)) {
            return hasData ? ExternalInfoSectionState.STALE : ExternalInfoSectionState.PENDING;
        }
        return switch (snapshot.getStatus()) {
            case READY -> ExternalInfoSectionState.READY;
            case EMPTY -> ExternalInfoSectionState.EMPTY;
            case ERROR -> hasData ? ExternalInfoSectionState.STALE : ExternalInfoSectionState.ERROR;
            case NOT_CONFIGURED -> ExternalInfoSectionState.NOT_CONFIGURED;
        };
    }

    private boolean needsRefresh(ExternalInfoSnapshot snapshot) {
        return snapshot == null || snapshot.getExpiresAt() == null || !snapshot.getExpiresAt().isAfter(Instant.now());
    }

    private Instant fetchedAt(ExternalInfoSnapshot snapshot) {
        return snapshot == null ? null : snapshot.getFetchedAt();
    }

    private Instant expiresAt(ExternalInfoSnapshot snapshot) {
        return snapshot == null ? null : snapshot.getExpiresAt();
    }

    private boolean supportsAvailability(MediaType type) {
        return type == MediaType.MOVIE || type == MediaType.SERIES
                || type == MediaType.ALBUM || type == MediaType.TRACK;
    }

    private boolean supportsRatings(MediaType type) {
        return type == MediaType.MOVIE || type == MediaType.SERIES;
    }

    private String attribution(ExternalSource source) {
        return switch (source) {
            case JUSTWATCH -> "JustWatch";
            case MUSICBRAINZ -> "MusicBrainz";
            default -> source.name();
        };
    }
}
