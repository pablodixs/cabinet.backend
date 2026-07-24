package com.scriptles.cabinet.media.command;

import com.scriptles.cabinet.media.catalog.CatalogResolver;
import com.scriptles.cabinet.media.dto.request.MediaTarget;
import com.scriptles.cabinet.media.dto.response.RatingResponse;
import lombok.RequiredArgsConstructor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RatingInteractionFacade {
    private final CatalogResolver catalogResolver;
    private final RatingInteractionWriter writer;
    private final MeterRegistry meterRegistry;

    public RatingResponse upsert(UUID userId, MediaTarget target, BigDecimal rating) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            return doUpsert(userId, target, rating);
        } finally {
            sample.stop(meterRegistry.timer(
                    "cabinet.catalog.interaction",
                    "operation", "rating",
                    "media_type", target.mediaType() == null ? "local" : target.mediaType().name()
            ));
        }
    }

    private RatingResponse doUpsert(UUID userId, MediaTarget target, BigDecimal rating) {
        CatalogResolver.Resolution resolution = catalogResolver.resolve(target);
        try {
            return writer.upsert(userId, target, resolution, rating);
        } catch (DataIntegrityViolationException race) {
            if (target.mediaId() != null) throw race;
            CatalogResolver.Resolution winner = catalogResolver.resolve(target);
            if (!winner.alreadyMaterialized()) throw race;
            return writer.upsert(userId, target, winner, rating);
        }
    }
}
