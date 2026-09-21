package com.scriptles.cabinet.catalog.service;

import com.scriptles.cabinet.catalog.collection.CollectionSourceMode;
import com.scriptles.cabinet.catalog.collection.CollectionType;
import com.scriptles.cabinet.catalog.repository.CollectionExternalReferenceRepository;
import com.scriptles.cabinet.catalog.event.TmdbCollectionReferenceLinkedEvent;
import com.scriptles.cabinet.media.enums.ExternalSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;

import static com.scriptles.cabinet.catalog.service.CatalogJobTypes.PRIORITY_RELATIONSHIP_DISCOVERY;
import static com.scriptles.cabinet.catalog.service.CatalogJobTypes.PRIORITY_STALE_REFRESH;

@Component
@Slf4j
public class CollectionEnrichmentScheduler {
    private final CollectionExternalReferenceRepository references;
    private final CatalogJobOrchestrationService orchestration;
    private final String locale;

    public CollectionEnrichmentScheduler(CollectionExternalReferenceRepository references,
            CatalogJobOrchestrationService orchestration,
            @Value("${catalog.collections.tmdb-sync.locale:pt-BR}") String locale) {
        this.references = references;
        this.orchestration = orchestration;
        this.locale = locale;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void referenceLinked(TmdbCollectionReferenceLinkedEvent event) {
        try {
            orchestration.syncCollection(event.collectionId(), locale, "RELATIONSHIP_DISCOVERY",
                    PRIORITY_RELATIONSHIP_DISCOVERY, null);
        } catch (RuntimeException failure) {
            log.warn("Unable to queue TMDB collection hydration for {}: {}",
                    event.collectionId(), failure.getMessage());
        }
    }

    @Scheduled(cron = "${catalog.collections.tmdb-sync.cron}", zone = "${catalog.collections.tmdb-sync.zone}")
    public void syncStaleCollections() {
        var due = references.findCollectionIdsDueForSync(ExternalSource.TMDB, CollectionType.FILM_SERIES,
                CollectionSourceMode.EXTERNAL, Instant.now(), PageRequest.of(0, 100));
        for (var collectionId : due) {
            try {
                orchestration.syncCollection(collectionId, locale, "STALE_REFRESH", PRIORITY_STALE_REFRESH, null);
            } catch (RuntimeException failure) {
                log.warn("Unable to queue stale TMDB collection {}: {}", collectionId, failure.getMessage());
            }
        }
    }
}
