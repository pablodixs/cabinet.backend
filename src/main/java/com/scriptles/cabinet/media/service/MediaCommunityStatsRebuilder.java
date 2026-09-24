package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.media.repository.MediaCommunityStatsRepository;
import com.scriptles.cabinet.media.repository.MediaRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MediaCommunityStatsRebuilder {
    private final MediaCommunityStatsRepository statsRepository;
    private final MediaRepository mediaRepository;
    private final MeterRegistry meters;

    @Transactional
    public void rebuild(UUID mediaId) {
        if (!mediaRepository.existsById(mediaId)) return;
        Timer.Sample sample = Timer.start(meters);
        String outcome = "success";

        // Domain mutations mark the row in their own transaction. Locking this version
        // serializes duplicate rebuilds and prevents clearing a newer dirty mark.
        try {
            var dirtyVersion = statsRepository.lockDirtyVersion(mediaId);
            var canonical = statsRepository.readCanonicalState(mediaId);
            var average = canonical.ratingCount() == 0
                    ? null
                    : canonical.ratingSum().divide(
                            java.math.BigDecimal.valueOf(canonical.ratingCount()), 12, RoundingMode.HALF_UP);

            statsRepository.upsert(mediaId, canonical, average);
            statsRepository.replaceDistribution(mediaId);
            dirtyVersion.ifPresent(version -> statsRepository.clearDirty(mediaId, version));
        } catch (RuntimeException failure) {
            outcome = "failure";
            meters.counter("cabinet.community.rebuild.failure").increment();
            throw failure;
        } finally {
            sample.stop(meters.timer("cabinet.community.rebuild.duration", "outcome", outcome));
        }
    }
}
