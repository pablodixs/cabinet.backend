package com.scriptles.cabinet.catalog.service;

import com.scriptles.cabinet.catalog.collection.CollectionSourceMode;
import com.scriptles.cabinet.catalog.collection.CollectionType;
import com.scriptles.cabinet.catalog.event.TmdbCollectionReferenceLinkedEvent;
import com.scriptles.cabinet.catalog.repository.CollectionExternalReferenceRepository;
import com.scriptles.cabinet.media.enums.ExternalSource;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollectionEnrichmentSchedulerTest {
    @Test
    void queuesDueExternalFilmCollectionsAsDurableJobs() {
        CollectionExternalReferenceRepository references = mock(CollectionExternalReferenceRepository.class);
        CatalogJobOrchestrationService orchestration = mock(CatalogJobOrchestrationService.class);
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        when(references.findCollectionIdsDueForSync(eq(ExternalSource.TMDB), eq(CollectionType.FILM_SERIES),
                eq(CollectionSourceMode.EXTERNAL), any(Instant.class), eq(PageRequest.of(0, 100))))
                .thenReturn(List.of(firstId, secondId));

        new CollectionEnrichmentScheduler(references, orchestration, "pt-BR").syncStaleCollections();

        verify(orchestration).syncCollection(firstId, "pt-BR", "STALE_REFRESH", 50, null);
        verify(orchestration).syncCollection(secondId, "pt-BR", "STALE_REFRESH", 50, null);
    }

    @Test
    void relationshipDiscoveryQueuesAHighPriorityHydration() {
        CollectionExternalReferenceRepository references = mock(CollectionExternalReferenceRepository.class);
        CatalogJobOrchestrationService orchestration = mock(CatalogJobOrchestrationService.class);
        UUID collectionId = UUID.randomUUID();

        new CollectionEnrichmentScheduler(references, orchestration, "en-US")
                .referenceLinked(new TmdbCollectionReferenceLinkedEvent(collectionId));

        verify(orchestration).syncCollection(collectionId, "en-US", "RELATIONSHIP_DISCOVERY", 80, null);
    }
}
