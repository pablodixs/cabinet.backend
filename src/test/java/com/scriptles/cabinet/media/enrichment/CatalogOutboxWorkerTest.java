package com.scriptles.cabinet.media.enrichment;

import com.scriptles.cabinet.media.catalog.CatalogEventPayload;
import com.scriptles.cabinet.media.entity.CatalogOutboxEvent;
import com.scriptles.cabinet.media.enums.CatalogEventType;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.external.ExternalMedia;
import com.scriptles.cabinet.media.external.ExternalMediaProvider;
import com.scriptles.cabinet.media.external.ExternalMediaProviderRegistry;
import com.scriptles.cabinet.media.external.WikidataClient;
import com.scriptles.cabinet.media.repository.CatalogOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.time.Duration;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.inOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogOutboxWorkerTest {
    @Mock
    private CatalogOutboxRepository repository;
    @Mock
    private CatalogOutboxClaimService claimService;
    @Mock
    private ExternalMediaProviderRegistry providerRegistry;
    @Mock
    private WikidataClient wikidataClient;
    @Mock
    private CatalogEnrichmentPersistenceService persistenceService;
    @Mock
    private ThreadPoolTaskExecutor executor;

    private CatalogOutboxWorker worker;

    @BeforeEach
    void setUp() {
        worker = new CatalogOutboxWorker(
                repository,
                claimService,
                providerRegistry,
                wikidataClient,
                persistenceService,
                executor,
                Duration.ofMinutes(15),
                new SimpleMeterRegistry()
        );
    }

    @Test
    void exposesStructureBeforeOptionalEnrichmentsAndCompletesWhenWikidataFails() {
        UUID eventId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        CatalogOutboxEvent event = new CatalogOutboxEvent();
        event.setEventType(CatalogEventType.MEDIA_CORE_MATERIALIZED);
        event.setAggregateId(mediaId);
        event.setPayload(new CatalogEventPayload(
                ExternalSource.TMDB,
                "550",
                MediaType.MOVIE,
                "en-US"
        ));
        ExternalMediaProvider provider = mock(ExternalMediaProvider.class);
        ExternalMedia primary = new ExternalMedia(
                ExternalSource.TMDB, "550", MediaType.MOVIE, "Fight Club",
                "Fight Club", null, null, null, null, null, null, "en", "US",
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                List.of(), List.of(), List.of(), List.of()
        );
        ExternalMedia portuguese = new ExternalMedia(
                ExternalSource.TMDB, "550", MediaType.MOVIE, "Clube da Luta",
                "Fight Club", null, null, null, null, null, null, "en", "US",
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                List.of(), List.of(), List.of(), List.of()
        );
        IllegalStateException wikidataFailure =
                new IllegalStateException("wikidata unavailable");

        when(repository.findById(eventId)).thenReturn(Optional.of(event));
        when(providerRegistry.get(ExternalSource.TMDB, MediaType.MOVIE)).thenReturn(provider);
        when(provider.findEnrichmentById(MediaType.MOVIE, "550", "en-US"))
                .thenReturn(Optional.of(primary));
        when(provider.findCoreById(MediaType.MOVIE, "550", "pt-BR"))
                .thenReturn(Optional.of(portuguese));
        when(wikidataClient.find(ExternalSource.TMDB, MediaType.MOVIE, "550", "en-US"))
                .thenThrow(wikidataFailure);

        worker.process(eventId);

        InOrder order = inOrder(provider, persistenceService, wikidataClient);
        order.verify(provider).findEnrichmentById(MediaType.MOVIE, "550", "en-US");
        order.verify(persistenceService).saveStructure(mediaId, primary);
        order.verify(provider).findCoreById(MediaType.MOVIE, "550", "pt-BR");
        order.verify(persistenceService).saveTranslation(mediaId, portuguese, "pt-BR");
        order.verify(wikidataClient).find(ExternalSource.TMDB, MediaType.MOVIE, "550", "en-US");
        order.verify(persistenceService).complete(mediaId, primary, "en-US", null);
        verify(claimService).complete(eventId);
        verify(claimService, never()).retry(eventId, wikidataFailure);
    }

    @Test
    void claimsOnlyTheExecutorCapacityAndDispatchesInParallel() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(executor.getMaxPoolSize()).thenReturn(4);
        when(executor.getActiveCount()).thenReturn(2);
        when(claimService.claim(anyString(), org.mockito.ArgumentMatchers.eq(2)))
                .thenReturn(List.of(first, second));

        worker.poll();

        verify(claimService).releaseStale(Duration.ofMinutes(15));
        verify(claimService).claim(anyString(), org.mockito.ArgumentMatchers.eq(2));
        verify(executor, times(2)).execute(any(Runnable.class));
    }
}
