package com.scriptles.cabinet.catalog.service;

import com.scriptles.cabinet.catalog.entity.CatalogJob;
import com.scriptles.cabinet.catalog.repository.CatalogJobRepository;
import com.scriptles.cabinet.media.catalog.CatalogImportFacade;
import com.scriptles.cabinet.media.dto.request.MediaTarget;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.external.ExternalMedia;
import com.scriptles.cabinet.media.external.TmdbClient;
import com.scriptles.cabinet.media.external.TmdbCollectionSnapshot;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static com.scriptles.cabinet.catalog.service.CatalogJobTypes.*;

@Component
@Slf4j
public class CatalogJobWorker {
    private final CatalogJobRepository jobs;
    private final CatalogJobClaimService claimService;
    private final CatalogJobLifecycleService lifecycle;
    private final CollectionManifestWriter manifestWriter;
    private final CatalogImportFacade catalogImportFacade;
    private final CollectionSourceMaterializationWriter sourceMaterializationWriter;
    private final TmdbClient tmdbClient;
    private final TmdbCatalogIndexService indexService;
    private final ThreadPoolTaskExecutor executor;
    private final Duration lockTimeout;
    private final MeterRegistry meterRegistry;

    public CatalogJobWorker(CatalogJobRepository jobs,
            CatalogJobClaimService claimService,
            CatalogJobLifecycleService lifecycle,
            CollectionManifestWriter manifestWriter,
            CatalogImportFacade catalogImportFacade,
            CollectionSourceMaterializationWriter sourceMaterializationWriter,
            TmdbClient tmdbClient,
            TmdbCatalogIndexService indexService,
            @Qualifier("catalogJobTaskExecutor") ThreadPoolTaskExecutor executor,
            MeterRegistry meterRegistry,
            @Value("${catalog.jobs.lock-timeout:15m}") Duration lockTimeout) {
        this.jobs = jobs;
        this.claimService = claimService;
        this.lifecycle = lifecycle;
        this.manifestWriter = manifestWriter;
        this.catalogImportFacade = catalogImportFacade;
        this.sourceMaterializationWriter = sourceMaterializationWriter;
        this.tmdbClient = tmdbClient;
        this.indexService = indexService;
        this.executor = executor;
        this.meterRegistry = meterRegistry;
        this.lockTimeout = lockTimeout;
    }

    @Scheduled(fixedDelayString = "${catalog.jobs.poll-delay:1000}")
    public void poll() {
        int recovered = claimService.recoverStale(Instant.now().minus(lockTimeout));
        if (recovered > 0) log.warn("Recovered {} stale catalog jobs", recovered);
        int capacity = Math.max(0, executor.getMaxPoolSize() - executor.getActiveCount());
        if (capacity == 0) return;
        String workerId = ManagementFactory.getRuntimeMXBean().getName();
        for (CatalogJobClaimService.ClaimedJob claim : claimService.claim(workerId, capacity)) {
            try {
                executor.execute(() -> process(claim));
            } catch (RuntimeException failure) {
                lifecycle.submissionFailed(claim.jobId(), claim.attemptNumber(), failure);
            }
        }
    }

    private void process(CatalogJobClaimService.ClaimedJob claim) {
        CatalogJob job = jobs.findById(claim.jobId()).orElse(null);
        if (job == null) return;
        Timer.Sample timer = Timer.start(meterRegistry);
        try {
            Map<String, Object> metrics = switch (job.getJobType()) {
                case TMDB_COLLECTION_HYDRATE, COLLECTION_SYNC -> hydrate(job);
                case COLLECTION_ITEM_MATERIALIZE -> materialize(job);
                case EXTERNAL_CATALOG_INDEX -> index(job);
                default -> throw new IllegalArgumentException("Unsupported catalog job type: " + job.getJobType());
            };
            lifecycle.complete(job.getId(), claim.attemptNumber(), metrics);
        } catch (RuntimeException failure) {
            lifecycle.fail(job.getId(), claim.attemptNumber(), failure);
            log.warn("Catalog job {} failed: {}", job.getId(), failure.getMessage());
        } finally {
            timer.stop(meterRegistry.timer("cabinet.catalog.job.duration", "type", job.getJobType()));
        }
    }

    private Map<String, Object> hydrate(CatalogJob job) {
        String locale = string(job.getPayload().get("locale"), "pt-BR");
        TmdbCollectionSnapshot snapshot = tmdbClient.findCollectionById(job.getExternalId(), locale);
        if (!snapshot.complete()) {
            throw new IllegalStateException("TMDB collection snapshot was incomplete; source manifest was not changed");
        }
        var result = manifestWriter.persist(job.getOperationId(), job, locale, snapshot);
        return result.metrics();
    }

    private Map<String, Object> materialize(CatalogJob job) {
        String locale = string(job.getPayload().get("locale"), "pt-BR");
        sourceMaterializationWriter.markResolving(job.getCollectionId(), job.getExternalId());
        TmdbCollectionSnapshot.Movie movie = manifestWriter.seed(job.getCollectionId(), job.getExternalId());
        MediaTarget target = new MediaTarget(null, ExternalSource.TMDB, movie.externalId(), MediaType.MOVIE, locale);
        var result = catalogImportFacade.materializeSeed(target, ExternalMedia.tmdbMovieSeed(movie));
        int resolved = sourceMaterializationWriter.resolveAll(movie.externalId(), result.media().getId());
        boolean created = result.snapshot() != null;
        meterRegistry.counter(created ? "cabinet.catalog.collection_items.materialized"
                : "cabinet.catalog.collection_items.reused", "provider", "TMDB").increment(resolved);
        return Map.of("processedItems", Math.max(1, resolved), "createdItems", created ? 1 : 0,
                "updatedItems", 0, "unchangedItems", created ? 0 : 1);
    }

    private Map<String, Object> index(CatalogJob job) {
        String exportDate = job.getPayload().get("date").toString();
        var result = indexService.importCollections(exportDate, job.getOperationId());
        return Map.of("totalItems", result.discovered(), "processedItems", result.discovered(),
                "createdItems", result.discovered(), "updatedItems", 0,
                "unchangedItems", 0, "failedItems", 0, "removedItems", result.removed());
    }

    private String string(Object value, String fallback) {
        return value instanceof String text && !text.isBlank() ? text : fallback;
    }
}
