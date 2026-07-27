package com.scriptles.cabinet.media.translation;

import com.scriptles.cabinet.media.enrichment.CatalogEnrichmentPersistenceService;
import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.external.ExternalMedia;
import com.scriptles.cabinet.media.external.ExternalMediaProvider;
import com.scriptles.cabinet.media.external.ExternalMediaProviderRegistry;
import com.scriptles.cabinet.media.repository.MediaTranslationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogTranslationLoaderTest {
    @Mock
    private MediaTranslationRepository translationRepository;
    @Mock
    private ExternalMediaProviderRegistry providerRegistry;
    @Mock
    private CatalogEnrichmentPersistenceService persistenceService;

    @InjectMocks
    private CatalogTranslationLoader loader;

    @Test
    void loadsRequestedTranslationForPreviouslyImportedMedia() {
        UUID mediaId = UUID.randomUUID();
        Media media = new Media();
        media.setId(mediaId);
        media.setType(MediaType.MOVIE);
        media.setDefaultLocale("en-US");
        ExternalReference reference = new ExternalReference();
        reference.setSource(ExternalSource.TMDB);
        reference.setExternalId("550");
        ExternalMediaProvider provider = mock(ExternalMediaProvider.class);
        ExternalMedia portuguese = new ExternalMedia(
                ExternalSource.TMDB, "550", MediaType.MOVIE, "Clube da Luta",
                "Fight Club", null, null, null, null, null, null, "en", "US",
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                List.of(), List.of(), List.of(), List.of()
        );

        when(providerRegistry.get(ExternalSource.TMDB, MediaType.MOVIE)).thenReturn(provider);
        when(provider.findCoreById(MediaType.MOVIE, "550", "pt-BR"))
                .thenReturn(Optional.of(portuguese));

        loader.loadIfMissing(media, reference, "pt-BR");

        verify(persistenceService).saveTranslation(mediaId, portuguese, "pt-BR");
    }
}
