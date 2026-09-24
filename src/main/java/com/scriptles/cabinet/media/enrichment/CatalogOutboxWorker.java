package com.scriptles.cabinet.media.enrichment;

import com.scriptles.cabinet.media.entity.CatalogOutboxEvent;
import com.scriptles.cabinet.media.catalog.CatalogEventPayload;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.external.ExternalMedia;
import com.scriptles.cabinet.media.external.ExternalMediaProviderRegistry;
import com.scriptles.cabinet.media.external.ExternalMediaNotFoundException;
import com.scriptles.cabinet.media.external.WikidataClient;
import com.scriptles.cabinet.media.repository.CatalogOutboxRepository;
import com.scriptles.cabinet.media.service.CatalogMetadataSyncService;
import com.scriptles.cabinet.media.service.AlbumReleaseVersionSyncService;
import com.scriptles.cabinet.media.enums.CatalogEventType;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Component
@Slf4j
public class CatalogOutboxWorker {
    private final CatalogOutboxRepository repository;
    private final CatalogOutboxClaimService claimService;
    private final ExternalMediaProviderRegistry providerRegistry;
    private final WikidataClient wikidataClient;
    private final CatalogEnrichmentPersistenceService persistenceService;
    private final ThreadPoolTaskExecutor executor;
    private final Duration lockTimeout;
    private final MeterRegistry meters;
    private CatalogMetadataSyncService metadataSyncService;
    private AlbumReleaseVersionSyncService albumReleaseVersionSyncService;

    public CatalogOutboxWorker(
            CatalogOutboxRepository repository,
            CatalogOutboxClaimService claimService,
            ExternalMediaProviderRegistry providerRegistry,
            WikidataClient wikidataClient,
            CatalogEnrichmentPersistenceService persistenceService,
            @Qualifier("catalogOutboxTaskExecutor") ThreadPoolTaskExecutor executor,
            @Value("${catalog.outbox.lock-timeout:15m}") Duration lockTimeout,
            MeterRegistry meters
    ) {
        this.repository = repository;
        this.claimService = claimService;
        this.providerRegistry = providerRegistry;
        this.wikidataClient = wikidataClient;
        this.persistenceService = persistenceService;
        this.executor = executor;
        this.lockTimeout = lockTimeout;
        this.meters = meters;
    }

    @org.springframework.beans.factory.annotation.Autowired
    void setMetadataSyncService(CatalogMetadataSyncService metadataSyncService) {
        this.metadataSyncService = metadataSyncService;
    }

    @org.springframework.beans.factory.annotation.Autowired
    void setAlbumReleaseVersionSyncService(AlbumReleaseVersionSyncService albumReleaseVersionSyncService) {
        this.albumReleaseVersionSyncService = albumReleaseVersionSyncService;
    }

    @Scheduled(fixedDelayString = "${catalog.outbox.poll-delay:1000}")
    public void poll() {
        int released = claimService.releaseStale(lockTimeout);
        if (released > 0) {
            log.warn("Released {} stale catalog enrichment event(s)", released);
        }
        int capacity = Math.max(0, executor.getMaxPoolSize() - executor.getActiveCount());
        if (capacity == 0) return;

        String workerId = ManagementFactory.getRuntimeMXBean().getName();
        for (UUID eventId : claimService.claim(workerId, capacity)) {
            try {
                executor.execute(() -> process(eventId));
            } catch (RuntimeException failure) {
                claimService.release(eventId);
                log.warn("Unable to submit catalog enrichment event {}", eventId, failure);
            }
        }
    }

    void process(UUID eventId) {
        CatalogOutboxEvent event = repository.findById(eventId).orElse(null);
        if (event == null) return;
        String eventType = event.getEventType().name();
        Timer.Sample sample = Timer.start(meters);
        try {
            CatalogEventPayload payload = event.getPayload();
            if (event.getEventType() == CatalogEventType.MEDIA_REFRESH_REQUESTED) {
                metadataSyncService.synchronize(event.getAggregateId(),
                        payload.reason() == null
                                ? com.scriptles.cabinet.media.enums.CatalogSyncReason.IMPORT_ENRICHMENT
                                : payload.reason());
                claimService.complete(eventId);
                recordCompleted(eventType);
                return;
            }
            if (event.getEventType() == CatalogEventType.ALBUM_RELEASE_VERSIONS_SYNC_REQUESTED) {
                albumReleaseVersionSyncService.synchronize(event.getAggregateId(), payload.externalId());
                claimService.complete(eventId);
                recordCompleted(eventType);
                return;
            }
            persistenceService.markEnriching(event.getAggregateId());
            ExternalMedia external = providerRegistry.get(payload.source(), payload.mediaType())
                    .findEnrichmentById(payload.mediaType(), payload.externalId(), payload.locale())
                    .orElseThrow(() -> new IllegalArgumentException("External media not found"));
            persistenceService.saveStructure(event.getAggregateId(), external);
            fetchSecondaryTranslation(event.getAggregateId(), payload);
            Optional<WikidataClient.WikidataEnrichment> wikidata = findWikidataSafely(payload, external);
            if (wikidata.isPresent()) {
                external = external.withEnrichment(
                        wikidata.get().logoUrl(),
                        wikidata.get().genres()
                );
            }
            String wikidataId = external.wikidataId() != null
                    ? external.wikidataId()
                    : wikidata.map(WikidataClient.WikidataEnrichment::wikidataId).orElse(null);
            persistenceService.complete(event.getAggregateId(), external, payload.locale(), wikidataId);
            claimService.complete(eventId);
            recordCompleted(eventType);
        } catch (RuntimeException failure) {
            boolean retrying = claimService.retry(eventId, failure);
            meters.counter("cabinet.outbox.event.failure", "queue", "catalog", "event_type", eventType)
                    .increment();
            meters.counter("cabinet.outbox.event.total", "queue", "catalog", "event_type", eventType,
                    "outcome", retrying ? "retry" : "dead").increment();
            if (!retrying && event.getEventType() != CatalogEventType.MEDIA_REFRESH_REQUESTED
                    && event.getEventType() != CatalogEventType.ALBUM_RELEASE_VERSIONS_SYNC_REQUESTED) {
                persistenceService.markFailed(event.getAggregateId(), failure.getMessage());
            }
            log.warn("Catalog enrichment event {} failed: {}", eventId, failure.getMessage());
        } finally {
            sample.stop(meters.timer("cabinet.outbox.processing.duration", "queue", "catalog",
                    "event_type", eventType));
        }
    }

    private void recordCompleted(String eventType) {
        meters.counter("cabinet.outbox.event.total", "queue", "catalog", "event_type", eventType,
                "outcome", "completed").increment();
    }

    private void fetchSecondaryTranslation(
            UUID mediaId,
            CatalogEventPayload payload
    ) {
        if (payload.source() == ExternalSource.MUSICBRAINZ
                || payload.source() == ExternalSource.GOOGLE_BOOKS) {
            return;
        }
        String secondaryLocale = "pt-BR".equals(payload.locale()) ? "en-US" : "pt-BR";
        try {
            providerRegistry.get(payload.source(), payload.mediaType())
                    .findCoreById(payload.mediaType(), payload.externalId(), secondaryLocale)
                    .ifPresent(media -> persistenceService.saveTranslation(mediaId, media, secondaryLocale));
        } catch (RuntimeException failure) {
            log.warn("Secondary translation for media {} failed: {}", mediaId, failure.getMessage());
        }
    }

    private Optional<WikidataClient.WikidataEnrichment> findWikidataSafely(
            CatalogEventPayload payload,
            ExternalMedia external
    ) {
        try {
            return findWikidata(payload, external);
        } catch (RuntimeException failure) {
            log.warn("Wikidata enrichment for {} {} failed: {}",
                    payload.mediaType(), payload.externalId(), failure.getMessage());
            return Optional.empty();
        }
    }

    private Optional<WikidataClient.WikidataEnrichment> findWikidata(
            CatalogEventPayload payload,
            ExternalMedia external
    ) {
        if (payload.mediaType() == MediaType.BOOK) {
            return wikidataClient.findBook(
                    payload.externalId(), external.isbn13(), external.isbn10(), payload.locale());
        }
        if (external.wikidataId() != null) {
            return wikidataClient.findById(external.wikidataId(), payload.locale());
        }
        return wikidataClient.find(
                payload.source(), payload.mediaType(), payload.externalId(), payload.locale());
    }
}
