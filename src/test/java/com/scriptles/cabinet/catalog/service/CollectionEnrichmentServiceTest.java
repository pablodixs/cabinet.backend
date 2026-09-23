package com.scriptles.cabinet.catalog.service;

import com.scriptles.cabinet.catalog.collection.CollectionType;
import com.scriptles.cabinet.catalog.entity.Collection;
import com.scriptles.cabinet.catalog.entity.CollectionExternalReference;
import com.scriptles.cabinet.catalog.repository.CollectionExternalReferenceRepository;
import com.scriptles.cabinet.catalog.repository.CollectionRepository;
import com.scriptles.cabinet.media.catalog.CatalogImportFacade;
import com.scriptles.cabinet.media.dto.request.MediaTarget;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.external.ExternalMediaException;
import com.scriptles.cabinet.media.external.TmdbClient;
import com.scriptles.cabinet.media.external.TmdbCollectionSnapshot;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollectionEnrichmentServiceTest {
    @Test
    void materializesEveryDistinctTmdbMovieThroughTheCatalogFacade() {
        UUID collectionId = UUID.randomUUID();
        Collection collection = collection(collectionId);
        CollectionExternalReference reference = reference(collection, "collection-10");
        TmdbCollectionSnapshot snapshot = new TmdbCollectionSnapshot("collection-10", "Collection", "Overview",
                null, null, List.of(movie("1"), movie("2"), movie("1")), true);
        CollectionRepository collections = mock(CollectionRepository.class);
        CollectionExternalReferenceRepository references = mock(CollectionExternalReferenceRepository.class);
        TmdbClient tmdb = mock(TmdbClient.class);
        CatalogImportFacade imports = mock(CatalogImportFacade.class);
        CollectionSyncWriter writer = mock(CollectionSyncWriter.class);
        when(collections.findById(collectionId)).thenReturn(Optional.of(collection));
        when(references.findByCollectionId(collectionId)).thenReturn(List.of(reference));
        when(tmdb.findCollectionById("collection-10", "en-US")).thenReturn(snapshot);
        when(imports.materialize(any(MediaTarget.class))).thenAnswer(invocation -> {
            MediaTarget target = invocation.getArgument(0);
            Media media = new Media();
            media.setId(UUID.fromString("00000000-0000-0000-0000-00000000000" + target.externalId()));
            return new CatalogImportFacade.Result(media, null);
        });

        new CollectionEnrichmentService(collections, references, tmdb, imports, writer)
                .sync(collectionId, "en-US");

        org.mockito.ArgumentCaptor<MediaTarget> targets = org.mockito.ArgumentCaptor.forClass(MediaTarget.class);
        verify(imports, org.mockito.Mockito.times(2)).materialize(targets.capture());
        assertThat(targets.getAllValues()).extracting(MediaTarget::source).containsOnly(ExternalSource.TMDB);
        assertThat(targets.getAllValues()).extracting(MediaTarget::mediaType).containsOnly(MediaType.MOVIE);
        assertThat(targets.getAllValues()).extracting(MediaTarget::externalId).containsExactly("1", "2");
        assertThat(targets.getAllValues()).extracting(MediaTarget::locale).containsOnly("en-US");
        verify(writer).reconcile(eq(collectionId), eq("collection-10"), eq(snapshot), anyList(), eq("en-US"), any());
    }

    @Test
    void leavesCollectionUntouchedWhenTmdbFetchFails() {
        UUID collectionId = UUID.randomUUID();
        Collection collection = collection(collectionId);
        CollectionExternalReference reference = reference(collection, "collection-10");
        CollectionRepository collections = mock(CollectionRepository.class);
        CollectionExternalReferenceRepository references = mock(CollectionExternalReferenceRepository.class);
        TmdbClient tmdb = mock(TmdbClient.class);
        CatalogImportFacade imports = mock(CatalogImportFacade.class);
        CollectionSyncWriter writer = mock(CollectionSyncWriter.class);
        when(collections.findById(collectionId)).thenReturn(Optional.of(collection));
        when(references.findByCollectionId(collectionId)).thenReturn(List.of(reference));
        when(tmdb.findCollectionById("collection-10", "pt-BR"))
                .thenThrow(new ExternalMediaException("TMDB unavailable"));

        assertThatThrownBy(() -> new CollectionEnrichmentService(collections, references, tmdb, imports, writer)
                .sync(collectionId, null)).isInstanceOf(ExternalMediaException.class);

        verify(imports, never()).materialize(any());
        verify(writer, never()).reconcile(any(), any(), any(), anyList(), any(), any());
        assertThat(collection.getLastSyncedAt()).isNull();
        assertThat(reference.getLastSyncedAt()).isNull();
    }

    @Test
    void doesNotReconcileAnIncompleteTmdbPartsList() {
        UUID collectionId = UUID.randomUUID();
        Collection collection = collection(collectionId);
        CollectionExternalReference reference = reference(collection, "collection-10");
        CollectionRepository collections = mock(CollectionRepository.class);
        CollectionExternalReferenceRepository references = mock(CollectionExternalReferenceRepository.class);
        TmdbClient tmdb = mock(TmdbClient.class);
        CatalogImportFacade imports = mock(CatalogImportFacade.class);
        CollectionSyncWriter writer = mock(CollectionSyncWriter.class);
        when(collections.findById(collectionId)).thenReturn(Optional.of(collection));
        when(references.findByCollectionId(collectionId)).thenReturn(List.of(reference));
        when(tmdb.findCollectionById("collection-10", "pt-BR")).thenReturn(new TmdbCollectionSnapshot(
                "collection-10", "Collection", null, null, null, List.of(movie("1")), false));

        assertThatThrownBy(() -> new CollectionEnrichmentService(collections, references, tmdb, imports, writer)
                .sync(collectionId, null)).isInstanceOf(ExternalMediaException.class);

        verify(imports, never()).materialize(any());
        verify(writer, never()).reconcile(any(), any(), any(), anyList(), any(), any());
    }

    private Collection collection(UUID id) {
        Collection collection = new Collection();
        collection.setId(id);
        collection.setType(CollectionType.FILM_SERIES);
        return collection;
    }

    private CollectionExternalReference reference(Collection collection, String externalId) {
        CollectionExternalReference reference = new CollectionExternalReference();
        reference.setCollection(collection);
        reference.setProvider(ExternalSource.TMDB);
        reference.setExternalId(externalId);
        return reference;
    }

    private TmdbCollectionSnapshot.Movie movie(String id) {
        return new TmdbCollectionSnapshot.Movie(id, "Movie " + id, null,
                LocalDate.of(2020, 1, 1), null);
    }
}
