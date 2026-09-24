package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.media.repository.MediaSearchDocumentRepository;
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
public class MediaSearchDocumentReconcileJob {
    private final MediaSearchDocumentRepository repository;
    private final MediaSearchDocumentRebuilder rebuilder;
    private final int batchSize;
    private final Duration staleAfter;

    public MediaSearchDocumentReconcileJob(
            MediaSearchDocumentRepository repository,
            MediaSearchDocumentRebuilder rebuilder,
            @Value("${media.search.local.reconcile.batch-size:50}") int batchSize,
            @Value("${media.search.local.reconcile.stale-after:30d}") Duration staleAfter
    ) {
        this.repository = repository;
        this.rebuilder = rebuilder;
        this.batchSize = Math.max(1, batchSize);
        this.staleAfter = staleAfter;
    }

    @Scheduled(fixedDelayString = "${media.search.local.reconcile.poll-delay:10m}")
    public void reconcile() {
        LinkedHashSet<UUID> candidates = new LinkedHashSet<>(repository.findMissingMediaIds(batchSize));
        if (candidates.size() < batchSize) {
            candidates.addAll(repository.findStaleMediaIds(
                    Instant.now().minus(staleAfter), batchSize - candidates.size()));
        }

        int rebuilt = 0;
        for (UUID mediaId : candidates) {
            try {
                rebuilder.rebuild(mediaId);
                rebuilt++;
            } catch (RuntimeException failure) {
                log.warn("Unable to reconcile media search document for {}: {}", mediaId, failure.getMessage());
            }
        }
        if (rebuilt > 0) log.info("Reconciled media search documents for {} media record(s)", rebuilt);
    }
}
