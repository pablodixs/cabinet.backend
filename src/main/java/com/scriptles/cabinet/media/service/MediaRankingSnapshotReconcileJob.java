package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.media.repository.MediaRankingSnapshotRepository;
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
public class MediaRankingSnapshotReconcileJob {
    private final MediaRankingSnapshotRepository snapshotRepository;
    private final MediaRankingSnapshotRebuilder rebuilder;
    private final int batchSize;
    private final Duration staleAfter;

    public MediaRankingSnapshotReconcileJob(
            MediaRankingSnapshotRepository snapshotRepository,
            MediaRankingSnapshotRebuilder rebuilder,
            @Value("${media.trending.snapshot.reconcile.batch-size:100}") int batchSize,
            @Value("${media.trending.snapshot.reconcile.stale-after:7d}") Duration staleAfter
    ) {
        this.snapshotRepository = snapshotRepository;
        this.rebuilder = rebuilder;
        this.batchSize = Math.max(1, batchSize);
        this.staleAfter = staleAfter;
    }

    @Scheduled(fixedDelayString = "${media.trending.snapshot.reconcile.poll-delay:5m}")
    public void reconcile() {
        LinkedHashSet<UUID> candidates = new LinkedHashSet<>();
        addWithinLimit(candidates, snapshotRepository.findDirtyMediaIds(batchSize));
        if (candidates.size() < batchSize) {
            addWithinLimit(candidates,
                    snapshotRepository.claimBackfillBatch(batchSize - candidates.size()));
        }
        if (candidates.size() < batchSize) {
            addWithinLimit(candidates, snapshotRepository.findStaleMediaIds(
                    Instant.now().minus(staleAfter), batchSize - candidates.size()));
        }

        int rebuilt = 0;
        for (UUID mediaId : candidates) {
            try {
                rebuilder.rebuild(mediaId);
                rebuilt++;
            } catch (RuntimeException failure) {
                log.warn("Unable to reconcile ranking snapshot for media {}: {}",
                        mediaId, failure.getMessage());
            }
        }
        if (rebuilt > 0) log.info("Reconciled ranking snapshot for {} media record(s)", rebuilt);
    }

    private void addWithinLimit(LinkedHashSet<UUID> target, java.util.List<UUID> source) {
        for (UUID mediaId : source) {
            if (target.size() >= batchSize) return;
            target.add(mediaId);
        }
    }
}
