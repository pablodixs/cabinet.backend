package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.media.enrichment.CatalogOutboxPublisher;
import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.SeriesDetails;
import com.scriptles.cabinet.media.enums.CatalogSyncReason;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.enums.ProviderAvailability;
import com.scriptles.cabinet.media.repository.ExternalReferenceRepository;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.media.repository.SeriesDetailsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CatalogMetadataRefreshScheduler {
    private final MediaRepository mediaRepository;
    private final ExternalReferenceRepository referenceRepository;
    private final SeriesDetailsRepository seriesDetailsRepository;
    private final CatalogOutboxPublisher publisher;
    private final CatalogStalenessPolicy stalenessPolicy;

    public void scheduleIfStale(UUID mediaId) {
        Media media = mediaRepository.findById(mediaId).orElse(null);
        if (media == null || (media.getType() != MediaType.MOVIE && media.getType() != MediaType.SERIES)) return;
        ExternalReference reference = referenceRepository.findByMediaIdAndSource(mediaId, ExternalSource.TMDB).orElse(null);
        if (reference == null) return;
        if (reference.getProviderAvailability() == ProviderAvailability.UNAVAILABLE) return;
        SeriesDetails details = media.getType() == MediaType.SERIES
                ? seriesDetailsRepository.findById(mediaId).orElse(null) : null;
        if (stalenessPolicy.isStale(media, reference.getLastSyncedAt(), details == null ? null : details.getStatus())) {
            schedule(media, reference, CatalogSyncReason.STALE_ACCESS);
        }
    }

    public void schedule(UUID mediaId, CatalogSyncReason reason) {
        Media media = mediaRepository.findById(mediaId)
                .orElseThrow(() -> new IllegalArgumentException("Media not found: " + mediaId));
        ExternalReference reference = referenceRepository.findByMediaIdAndSource(mediaId, ExternalSource.TMDB)
                .orElseThrow(() -> new IllegalStateException("TMDB reference missing for " + mediaId));
        if (media.getType() != MediaType.MOVIE && media.getType() != MediaType.SERIES) {
            throw new IllegalArgumentException("TMDB refresh does not support " + media.getType());
        }
        schedule(media, reference, reason);
    }

    private void schedule(Media media, ExternalReference reference, CatalogSyncReason reason) {
        publisher.publishRefresh(media.getId(), ExternalSource.TMDB, reference.getExternalId(),
                media.getType(), media.getDefaultLocale(), reason);
    }
}
