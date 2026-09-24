package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.media.repository.MediaCommunityStatsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.UUID;

@Component
@Slf4j
public class MediaCommunityStatsReconcileJob {
    private final MediaCommunityStatsRepository statsRepository;
    private final MediaCommunityStatsRebuilder rebuilder;
    private final int batchSize;
    private final Duration staleAfter;

    public MediaCommunityStatsReconcileJob(
            MediaCommunityStatsRepository statsRepository,
            MediaCommunityStatsRebuilder rebuilder,
            @Value("${media.community-stats.reconcile.batch-size:100}") int batchSize,
            @Value("${media.community-stats.reconcile.stale-after:7d}") Duration staleAfter
    ) {
        this.statsRepository = statsRepository;
        this.rebuilder = rebuilder;
        this.batchSize = Math.max(1, batchSize);
        this.staleAfter = staleAfter;
    }

    @Scheduled(fixedDelayString = "${media.community-stats.reconcile.poll-delay:5m}")
    public void reconcile() {
        LinkedHashSet<UUID> candidates = new LinkedHashSet<>();
        addWithinLimit(candidates, statsRepository.findDirtyMediaIds(batchSize));
        if (candidates.size() < batchSize) {
            addWithinLimit(candidates, statsRepository.findMissingMediaIds(batchSize - candidates.size()));
        }
        if (candidates.size() < batchSize) {
            addWithinLimit(candidates, statsRepository.findStaleMediaIds(
                    Instant.now().minus(staleAfter), batchSize - candidates.size()));
        }

        int rebuilt = 0;
        for (UUID mediaId : candidates) {
            try {
                // A marker makes reconciliation rebuilds serialize with event-triggered work.
                statsRepository.markDirty(mediaId);
                rebuilder.rebuild(mediaId);
                rebuilt++;
            } catch (RuntimeException failure) {
                log.warn("Unable to reconcile community statistics for media {}: {}",
                        mediaId, failure.getMessage());
            }
        }
        if (rebuilt > 0) log.info("Reconciled community stats for {} media record(s)", rebuilt);
    }

    private void addWithinLimit(LinkedHashSet<UUID> target, java.util.List<UUID> source) {
        for (UUID mediaId : source) {
            if (target.size() >= batchSize) return;
            target.add(mediaId);
        }
    }
}
