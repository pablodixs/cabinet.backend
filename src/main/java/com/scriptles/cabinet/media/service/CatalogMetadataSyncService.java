package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.CatalogSyncReason;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.enums.ProviderAvailability;
import com.scriptles.cabinet.media.external.ExternalMedia;
import com.scriptles.cabinet.media.external.ExternalMediaNotFoundException;
import com.scriptles.cabinet.media.external.ExternalMediaProviderRegistry;
import com.scriptles.cabinet.media.repository.ExternalReferenceRepository;
import com.scriptles.cabinet.media.repository.MediaRepository;
import lombok.RequiredArgsConstructor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CatalogMetadataSyncService {
    private final MediaRepository mediaRepository;
    private final ExternalReferenceRepository referenceRepository;
    private final CatalogMetadataPersistenceService persistenceService;
    private final ExternalMediaProviderRegistry providerRegistry;
    private final com.scriptles.cabinet.media.repository.MediaTranslationRepository translationRepository;
    private final MeterRegistry meterRegistry;

    @Transactional
    public void synchronize(UUID mediaId, CatalogSyncReason reason) {
        Timer.Sample timer = Timer.start(meterRegistry);
        Media media = mediaRepository.findById(mediaId)
                .orElseThrow(() -> new IllegalArgumentException("Media not found: " + mediaId));
        meterRegistry.counter("cabinet.catalog.sync.requested", "media_type", media.getType().name(),
                "source", ExternalSource.TMDB.name(), "reason", reason.name()).increment();
        if (media.getType() != MediaType.MOVIE && media.getType() != MediaType.SERIES) {
            throw new IllegalArgumentException("TMDB refresh does not support " + media.getType());
        }
        ExternalReference reference = referenceRepository
                .findByMediaIdAndSource(mediaId, ExternalSource.TMDB)
                .orElseThrow(() -> new IllegalStateException("TMDB reference missing for " + mediaId));

        ExternalMedia external;
        try {
            external = providerRegistry.get(ExternalSource.TMDB, media.getType())
                    .findEnrichmentById(media.getType(), reference.getExternalId(), media.getDefaultLocale())
                    .orElseThrow(() -> new ExternalMediaNotFoundException("TMDB media not found", null));
        } catch (ExternalMediaNotFoundException notFound) {
            reference.setProviderAvailability(ProviderAvailability.UNAVAILABLE);
            reference.setProviderUnavailableAt(Instant.now());
            media.setLastSyncError(notFound.getMessage());
            referenceRepository.save(reference);
            mediaRepository.save(media);
            timer.stop(meterRegistry.timer("cabinet.catalog.sync.duration", "source", ExternalSource.TMDB.name(),
                    "reason", reason.name(), "outcome", "unavailable"));
            return;
        }

        persistenceService.update(mediaId, external, media.getDefaultLocale(), media.getWikidataId());
        if (translationRepository.existsByMediaIdAndLocale(mediaId, "en-US")
                && !"en-US".equalsIgnoreCase(media.getDefaultLocale())) {
            try {
                providerRegistry.get(ExternalSource.TMDB, media.getType())
                        .findEnrichmentById(media.getType(), reference.getExternalId(), "en-US")
                        .ifPresent(en -> persistenceService.updateTranslation(mediaId, en, "en-US"));
            } catch (RuntimeException secondaryFailure) {
                log.warn("Secondary TMDB translation refresh failed for {}: {}", mediaId,
                        secondaryFailure.getMessage());
            }
        }

        reference.setLastSyncedAt(Instant.now());
        reference.setProviderAvailability(ProviderAvailability.AVAILABLE);
        reference.setProviderUnavailableAt(null);
        referenceRepository.save(reference);
        media.setLastSyncError(null);
        mediaRepository.save(media);
        meterRegistry.counter("cabinet.catalog.sync.success", "source", ExternalSource.TMDB.name(),
                "reason", reason.name()).increment();
        timer.stop(meterRegistry.timer("cabinet.catalog.sync.duration", "source", ExternalSource.TMDB.name(),
                "reason", reason.name(), "outcome", "success"));
    }
}
