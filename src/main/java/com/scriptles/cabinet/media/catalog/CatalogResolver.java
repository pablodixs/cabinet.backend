package com.scriptles.cabinet.media.catalog;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.media.dto.request.MediaTarget;
import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.external.ExternalMedia;
import com.scriptles.cabinet.media.external.ExternalMediaProviderRegistry;
import com.scriptles.cabinet.media.repository.ExternalReferenceRepository;
import com.scriptles.cabinet.media.repository.MediaRepository;
import lombok.RequiredArgsConstructor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CatalogResolver {
    private final MediaRepository mediaRepository;
    private final ExternalReferenceRepository externalReferenceRepository;
    private final ExternalMediaProviderRegistry providerRegistry;
    private final CatalogSnapshotCache snapshotCache;
    private final MeterRegistry meterRegistry;

    public Resolution resolve(MediaTarget target) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = "success";
        try {
            return resolveInternal(target);
        } catch (RuntimeException failure) {
            outcome = "error";
            throw failure;
        } finally {
            sample.stop(meterRegistry.timer(
                    "cabinet.catalog.resolve",
                    "outcome", outcome,
                    "media_type", target.mediaType() == null ? "local" : target.mediaType().name()
            ));
        }
    }

    public Resolution resolveSeed(MediaTarget target, ExternalMedia seed) {
        Optional<ExternalReference> stored = externalReferenceRepository.findBySourceAndExternalId(
                target.source(), target.externalId());
        if (stored.isPresent()) {
            return new Resolution(stored.get().getMedia().getId(), null, target.normalizedLocale());
        }
        return new Resolution(null, new CatalogSnapshot(seed, target.normalizedLocale(), Instant.now()),
                target.normalizedLocale());
    }

    private Resolution resolveInternal(MediaTarget target) {
        if (target.mediaId() != null) {
            if (!mediaRepository.existsById(target.mediaId())) {
                throw notFound("MEDIA_NOT_FOUND", "Mídia não encontrada");
            }
            return new Resolution(target.mediaId(), null, target.normalizedLocale());
        }

        Optional<ExternalReference> stored = externalReferenceRepository.findBySourceAndExternalId(
                target.source(), target.externalId());
        if (stored.isPresent()) {
            return new Resolution(stored.get().getMedia().getId(), null, target.normalizedLocale());
        }

        String locale = target.normalizedLocale();
        CatalogSnapshot snapshot = snapshotCache.getOrLoad(
                target.source(),
                target.mediaType(),
                target.externalId(),
                locale,
                () -> providerRegistry.get(target.source(), target.mediaType())
                        .findCoreById(target.mediaType(), target.externalId(), locale)
                        .map(external -> new CatalogSnapshot(external, locale, Instant.now()))
        ).orElseThrow(() -> notFound("EXTERNAL_MEDIA_NOT_FOUND", "Mídia externa não encontrada"));
        return new Resolution(null, snapshot, locale);
    }

    private ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }

    public record Resolution(UUID mediaId, CatalogSnapshot snapshot, String locale) {
        public boolean alreadyMaterialized() {
            return mediaId != null;
        }
    }
}
