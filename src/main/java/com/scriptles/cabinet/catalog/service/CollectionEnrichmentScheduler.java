package com.scriptles.cabinet.catalog.service;

import com.scriptles.cabinet.catalog.collection.CollectionSourceMode;
import com.scriptles.cabinet.catalog.collection.CollectionType;
import com.scriptles.cabinet.catalog.event.TmdbCollectionReferenceLinkedEvent;
import com.scriptles.cabinet.catalog.repository.CollectionExternalReferenceRepository;
import com.scriptles.cabinet.media.enums.ExternalSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
public class CollectionEnrichmentScheduler {
    private final CollectionExternalReferenceRepository referenceRepository;
    private final CollectionEnrichmentService enrichmentService;
    private final Executor executor;
    private final Duration staleAfter;
    private final String locale;
    private final Set<UUID> queuedOrRunning = ConcurrentHashMap.newKeySet();
    private final ConcurrentLinkedQueue<UUID> pending = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean workerRunning = new AtomicBoolean();

    public CollectionEnrichmentScheduler(
            CollectionExternalReferenceRepository referenceRepository,
            CollectionEnrichmentService enrichmentService,
            @Qualifier("externalInfoTaskExecutor") Executor executor,
            @Value("${catalog.collections.tmdb-sync.stale-after:24h}") Duration staleAfter,
            @Value("${catalog.collections.tmdb-sync.locale:pt-BR}") String locale
    ) {
        this.referenceRepository = referenceRepository;
        this.enrichmentService = enrichmentService;
        this.executor = executor;
        this.staleAfter = staleAfter;
        this.locale = locale;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void referenceLinked(TmdbCollectionReferenceLinkedEvent event) {
        schedule(event.collectionId());
    }

    @Scheduled(
            cron = "${catalog.collections.tmdb-sync.cron:0 0 5 * * *}",
            zone = "${catalog.collections.tmdb-sync.zone:America/Sao_Paulo}"
    )
    public void syncStaleCollections() {
        Instant cutoff = Instant.now().minus(staleAfter);
        List<UUID> collectionIds = referenceRepository.findCollectionIdsDueForSync(
                ExternalSource.TMDB, CollectionType.FILM_SERIES, CollectionSourceMode.EXTERNAL, cutoff);
        collectionIds.forEach(this::schedule);
    }

    private void schedule(UUID collectionId) {
        if (!queuedOrRunning.add(collectionId)) return;
        pending.add(collectionId);
        startWorker();
    }

    private void startWorker() {
        if (!workerRunning.compareAndSet(false, true)) return;
        try {
            executor.execute(this::drainPending);
        } catch (RuntimeException failure) {
            UUID collectionId;
            while ((collectionId = pending.poll()) != null) queuedOrRunning.remove(collectionId);
            workerRunning.set(false);
            if (!pending.isEmpty()) startWorker();
            log.warn("Unable to schedule TMDB collection synchronization: {}", failure.getMessage());
        }
    }

    private void drainPending() {
        try {
            UUID collectionId;
            while ((collectionId = pending.poll()) != null) {
                try {
                    enrichmentService.sync(collectionId, locale);
                } catch (RuntimeException failure) {
                    log.warn("Unable to synchronize TMDB collection {}: {}", collectionId, failure.getMessage());
                } finally {
                    queuedOrRunning.remove(collectionId);
                }
            }
        } finally {
            workerRunning.set(false);
            if (!pending.isEmpty()) startWorker();
        }
    }
}
