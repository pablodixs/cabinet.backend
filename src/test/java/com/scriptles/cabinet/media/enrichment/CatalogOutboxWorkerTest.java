package com.scriptles.cabinet.media.enrichment;

import com.scriptles.cabinet.media.catalog.CatalogEventPayload;
import com.scriptles.cabinet.media.entity.CatalogOutboxEvent;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.external.ExternalMedia;
import com.scriptles.cabinet.media.external.ExternalMediaProvider;
import com.scriptles.cabinet.media.external.ExternalMediaProviderRegistry;
import com.scriptles.cabinet.media.external.WikidataClient;
import com.scriptles.cabinet.media.repository.CatalogOutboxRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
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

    @InjectMocks
    private CatalogOutboxWorker worker;

    @Test
    void savesSecondaryTranslationEvenWhenPrimaryEnrichmentFails() {
        UUID eventId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        CatalogOutboxEvent event = new CatalogOutboxEvent();
        event.setAggregateId(mediaId);
        event.setPayload(new CatalogEventPayload(
                ExternalSource.TMDB,
                "550",
                MediaType.MOVIE,
                "en-US"
        ));
        ExternalMediaProvider provider = mock(ExternalMediaProvider.class);
        ExternalMedia portuguese = new ExternalMedia(
                ExternalSource.TMDB, "550", MediaType.MOVIE, "Clube da Luta",
                "Fight Club", null, null, null, null, null, null, "en", "US",
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                List.of(), List.of(), List.of(), List.of()
        );
        IllegalStateException enrichmentFailure =
                new IllegalStateException("enrichment unavailable");

        when(repository.findById(eventId)).thenReturn(Optional.of(event));
        when(providerRegistry.get(ExternalSource.TMDB, MediaType.MOVIE)).thenReturn(provider);
        when(provider.findCoreById(MediaType.MOVIE, "550", "pt-BR"))
                .thenReturn(Optional.of(portuguese));
        when(provider.findEnrichmentById(MediaType.MOVIE, "550", "en-US"))
                .thenThrow(enrichmentFailure);
        when(claimService.retry(eventId, enrichmentFailure)).thenReturn(true);

        worker.process(eventId);

        verify(persistenceService).saveTranslation(mediaId, portuguese, "pt-BR");
    }
}
