package com.scriptles.cabinet.media.catalog;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.media.dto.request.MediaTarget;
import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.external.ExternalMedia;
import com.scriptles.cabinet.media.external.ExternalMediaProvider;
import com.scriptles.cabinet.media.external.ExternalMediaProviderRegistry;
import com.scriptles.cabinet.media.repository.ExternalReferenceRepository;
import com.scriptles.cabinet.media.repository.MediaRepository;
import org.junit.jupiter.api.Test;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class CatalogResolverTest {
    private final MediaRepository mediaRepository = mock(MediaRepository.class);
    private final ExternalReferenceRepository referenceRepository = mock(ExternalReferenceRepository.class);
    private final ExternalMediaProviderRegistry providerRegistry = mock(ExternalMediaProviderRegistry.class);
    private final ExternalMediaProvider provider = mock(ExternalMediaProvider.class);
    private final CatalogSnapshotCache cache = new CatalogSnapshotCache();
    private final CatalogResolver resolver = new CatalogResolver(
            mediaRepository, referenceRepository, providerRegistry, cache, new SimpleMeterRegistry());

    @Test
    void existingExternalReferenceNeverCallsProvider() {
        UUID mediaId = UUID.randomUUID();
        Media media = new Media();
        media.setId(mediaId);
        ExternalReference reference = new ExternalReference();
        reference.setMedia(media);
        when(referenceRepository.findBySourceAndExternalId(ExternalSource.TMDB, "550"))
                .thenReturn(Optional.of(reference));

        CatalogResolver.Resolution result = resolver.resolve(target("550"));

        assertThat(result.mediaId()).isEqualTo(mediaId);
        assertThat(result.snapshot()).isNull();
        verifyNoInteractions(providerRegistry);
    }

    @Test
    void reusesTheCoreSnapshotAcrossResolutions() {
        when(referenceRepository.findBySourceAndExternalId(ExternalSource.TMDB, "550"))
                .thenReturn(Optional.empty());
        when(providerRegistry.get(ExternalSource.TMDB, MediaType.MOVIE)).thenReturn(provider);
        when(provider.findCoreById(MediaType.MOVIE, "550", "pt-BR"))
                .thenReturn(Optional.of(movie("550")));

        CatalogResolver.Resolution first = resolver.resolve(target("550"));
        CatalogResolver.Resolution second = resolver.resolve(target("550"));

        assertThat(second.snapshot()).isSameAs(first.snapshot());
        verify(provider, times(1)).findCoreById(MediaType.MOVIE, "550", "pt-BR");
    }

    @Test
    void negativelyCachesMissingExternalMedia() {
        when(referenceRepository.findBySourceAndExternalId(ExternalSource.TMDB, "404"))
                .thenReturn(Optional.empty());
        when(providerRegistry.get(ExternalSource.TMDB, MediaType.MOVIE)).thenReturn(provider);
        when(provider.findCoreById(MediaType.MOVIE, "404", "pt-BR")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolve(target("404"))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> resolver.resolve(target("404"))).isInstanceOf(ApiException.class);

        verify(provider, times(1)).findCoreById(MediaType.MOVIE, "404", "pt-BR");
    }

    private MediaTarget target(String id) {
        return new MediaTarget(null, ExternalSource.TMDB, id, MediaType.MOVIE, "pt-BR");
    }

    private ExternalMedia movie(String id) {
        return new ExternalMedia(
                ExternalSource.TMDB, id, MediaType.MOVIE, "Fight Club", "Fight Club",
                null, null, null, null, null, null, "en", "US",
                null, null, null, null, null, null, 139, null, null,
                null, null, null, null, null, null, null, null, null,
                List.of(), List.of(), List.of(), List.of()
        );
    }
}
