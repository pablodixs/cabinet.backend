package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.media.entity.ExternalInfoSnapshot;
import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.MediaAvailabilityOffer;
import com.scriptles.cabinet.media.entity.MediaExternalRating;
import com.scriptles.cabinet.media.enums.ExternalInfoKind;
import com.scriptles.cabinet.media.enums.ExternalInfoSnapshotStatus;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.external.ExternalAvailability;
import com.scriptles.cabinet.media.external.ExternalRatingValue;
import com.scriptles.cabinet.media.repository.ExternalInfoSnapshotRepository;
import com.scriptles.cabinet.media.repository.ExternalReferenceRepository;
import com.scriptles.cabinet.media.repository.MediaAvailabilityOfferRepository;
import com.scriptles.cabinet.media.repository.MediaExternalRatingRepository;
import com.scriptles.cabinet.media.repository.MediaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExternalInfoPersistenceService {
    private final MediaRepository mediaRepository;
    private final ExternalReferenceRepository externalReferenceRepository;
    private final ExternalInfoSnapshotRepository snapshotRepository;
    private final MediaAvailabilityOfferRepository availabilityOfferRepository;
    private final MediaExternalRatingRepository externalRatingRepository;

    @Transactional
    public void replaceAvailability(
            UUID mediaId,
            String countryCode,
            ExternalAvailability availability,
            Duration ttl
    ) {
        Media media = mediaRepository.getReferenceById(mediaId);
        availabilityOfferRepository.deleteAllByMediaIdAndCountryCode(mediaId, countryCode);
        availabilityOfferRepository.flush();
        List<MediaAvailabilityOffer> offers = availability.offers().stream().map(value -> {
            MediaAvailabilityOffer offer = new MediaAvailabilityOffer();
            offer.setMedia(media);
            offer.setCountryCode(countryCode);
            offer.setDataSource(availability.source());
            offer.setProviderId(value.providerId());
            offer.setProviderName(value.providerName());
            offer.setLogoUrl(value.logoUrl());
            offer.setOfferType(value.type());
            offer.setExternalUrl(value.url());
            offer.setSourceUrl(availability.sourceUrl());
            offer.setDisplayPriority(value.displayPriority());
            return offer;
        }).toList();
        availabilityOfferRepository.saveAll(offers);
        updateSnapshot(
                media,
                ExternalInfoKind.AVAILABILITY,
                countryCode,
                offers.isEmpty() ? ExternalInfoSnapshotStatus.EMPTY : ExternalInfoSnapshotStatus.READY,
                ttl,
                null
        );
    }

    @Transactional
    public void replaceRatings(
            UUID mediaId,
            String imdbId,
            List<ExternalRatingValue> values,
            Duration ttl
    ) {
        Media media = mediaRepository.getReferenceById(mediaId);
        externalRatingRepository.deleteAllByMediaIdAndProvider(mediaId, ExternalSource.OMDB);
        externalRatingRepository.flush();
        List<MediaExternalRating> ratings = values.stream().map(value -> {
            MediaExternalRating rating = new MediaExternalRating();
            rating.setMedia(media);
            rating.setProvider(ExternalSource.OMDB);
            rating.setSource(value.source());
            rating.setMetric(value.metric());
            rating.setValue(value.value());
            rating.setScale(value.scale());
            rating.setDisplayValue(value.displayValue());
            rating.setExternalId(imdbId);
            return rating;
        }).toList();
        externalRatingRepository.saveAll(ratings);
        updateSnapshot(
                media,
                ExternalInfoKind.RATINGS,
                MediaExternalInfoService.GLOBAL_REGION,
                ratings.isEmpty() ? ExternalInfoSnapshotStatus.EMPTY : ExternalInfoSnapshotStatus.READY,
                ttl,
                null
        );
    }

    @Transactional
    public void markUnavailable(
            UUID mediaId,
            ExternalInfoKind kind,
            String region,
            ExternalInfoSnapshotStatus status,
            String errorCode,
            Duration retryAfter
    ) {
        updateSnapshot(
                mediaRepository.getReferenceById(mediaId),
                kind,
                region,
                status,
                retryAfter,
                errorCode
        );
    }

    @Transactional
    public String storeImdbReference(UUID mediaId, String imdbId) {
        ExternalReference reference = externalReferenceRepository.findByMediaIdAndSource(mediaId, ExternalSource.IMDB)
                .orElseGet(ExternalReference::new);
        if (reference.getId() == null) {
            reference.setMedia(mediaRepository.getReferenceById(mediaId));
            reference.setSource(ExternalSource.IMDB);
            reference.setPrimaryReference(false);
        }
        reference.setExternalId(imdbId);
        reference.setExternalUrl("https://www.imdb.com/title/" + imdbId);
        reference.setLastSyncedAt(Instant.now());
        return externalReferenceRepository.save(reference).getExternalId();
    }

    private void updateSnapshot(
            Media media,
            ExternalInfoKind kind,
            String region,
            ExternalInfoSnapshotStatus status,
            Duration ttl,
            String errorCode
    ) {
        ExternalInfoSnapshot snapshot = snapshotRepository
                .findByMediaIdAndKindAndRegion(media.getId(), kind, region)
                .orElseGet(ExternalInfoSnapshot::new);
        snapshot.setMedia(media);
        snapshot.setKind(kind);
        snapshot.setRegion(region);
        snapshot.setStatus(status);
        snapshot.setErrorCode(errorCode);
        Instant now = Instant.now();
        snapshot.setFetchedAt(now);
        snapshot.setExpiresAt(now.plus(ttl));
        snapshotRepository.save(snapshot);
    }
}
