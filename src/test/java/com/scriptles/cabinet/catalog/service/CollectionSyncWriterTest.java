package com.scriptles.cabinet.catalog.service;

import com.scriptles.cabinet.catalog.collection.CollectionSourceMode;
import com.scriptles.cabinet.catalog.collection.CollectionType;
import com.scriptles.cabinet.catalog.entity.Collection;
import com.scriptles.cabinet.catalog.entity.CollectionExternalReference;
import com.scriptles.cabinet.catalog.entity.CollectionItem;
import com.scriptles.cabinet.catalog.franchise.FranchiseMediaRelationType;
import com.scriptles.cabinet.catalog.repository.CollectionExternalReferenceRepository;
import com.scriptles.cabinet.catalog.repository.CollectionItemRepository;
import com.scriptles.cabinet.catalog.repository.CollectionRepository;
import com.scriptles.cabinet.catalog.repository.CollectionTranslationRepository;
import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.external.TmdbCollectionSnapshot;
import com.scriptles.cabinet.media.repository.ExternalReferenceRepository;
import com.scriptles.cabinet.media.repository.MediaRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollectionSyncWriterTest {
    @Test
    void addsTmdbManagedItemsAndUpdatesExternalMetadataAndDates() {
        Fixture fixture = new Fixture();
        TmdbCollectionSnapshot snapshot = snapshot(List.of(
                new TmdbCollectionSnapshot.Movie("1", "First", null, LocalDate.of(2010, 1, 1), null),
                new TmdbCollectionSnapshot.Movie("2", "Second", null, LocalDate.of(2022, 5, 5), null)
        ));
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        Media first = media(firstId);
        Media second = media(secondId);
        when(fixture.mediaRepository.getReferenceById(firstId)).thenReturn(first);
        when(fixture.mediaRepository.getReferenceById(secondId)).thenReturn(second);

        fixture.writer().reconcile(fixture.collection.getId(), "collection-10", snapshot,
                List.of(new CollectionSyncWriter.MaterializedMovie("1", firstId),
                        new CollectionSyncWriter.MaterializedMovie("2", secondId)), "pt-BR", Instant.parse("2025-01-01T00:00:00Z"));

        ArgumentCaptor<Iterable<CollectionItem>> savedItems = ArgumentCaptor.forClass(Iterable.class);
        verify(fixture.items).saveAll(savedItems.capture());
        assertThat(savedItems.getValue()).extracting(item -> item.getMedia().getId()).containsExactly(firstId, secondId);
        assertThat(savedItems.getValue()).allSatisfy(item -> {
            assertThat(item.isSourceManaged()).isTrue();
            assertThat(item.getSourceProvider()).isEqualTo(ExternalSource.TMDB);
            assertThat(item.getRelationType()).isEqualTo(FranchiseMediaRelationType.CORE);
        });
        assertThat(fixture.collection.getTitle()).isEqualTo("TMDB Collection");
        assertThat(fixture.collection.getDescription()).isEqualTo("Remote overview");
        assertThat(fixture.collection.getStartDate()).isEqualTo(LocalDate.of(2010, 1, 1));
        assertThat(fixture.collection.getEndDate()).isEqualTo(LocalDate.of(2022, 5, 5));
        assertThat(fixture.collection.getLastSyncedAt()).isEqualTo(fixture.reference.getLastSyncedAt());
    }

    @Test
    void removesOnlyTmdbManagedItemsAndPreservesManualLinks() {
        Fixture fixture = new Fixture();
        Media removedMedia = media(UUID.randomUUID());
        Media manualMedia = media(UUID.randomUUID());
        CollectionItem tmdbItem = item(fixture.collection, removedMedia, true, ExternalSource.TMDB, 1);
        CollectionItem manualItem = item(fixture.collection, manualMedia, false, null, 2);
        when(fixture.items.findByCollectionIdOrderByPositionAsc(fixture.collection.getId()))
                .thenReturn(List.of(tmdbItem, manualItem));
        when(fixture.mediaReferences.findAllByMediaIdIn(anyCollection()))
                .thenReturn(List.of(externalReference(removedMedia, "2"), externalReference(manualMedia, "9")));
        UUID addedId = UUID.randomUUID();
        when(fixture.mediaRepository.getReferenceById(addedId)).thenReturn(media(addedId));

        fixture.writer().reconcile(fixture.collection.getId(), "collection-10", snapshot(List.of(
                        new TmdbCollectionSnapshot.Movie("1", "Added", null, null, null))),
                List.of(new CollectionSyncWriter.MaterializedMovie("1", addedId)), "pt-BR", Instant.parse("2025-01-01T00:00:00Z"));

        verify(fixture.items).deleteAll(List.of(tmdbItem));
        verify(fixture.items).saveAll(org.mockito.ArgumentMatchers.<Iterable<CollectionItem>>argThat(items ->
                items.iterator().next().getMedia().getId().equals(addedId)));
        assertThat(manualItem.isSourceManaged()).isFalse();
    }

    @Test
    void doesNotRewriteAnUnchangedTmdbManagedItem() {
        Fixture fixture = new Fixture();
        Media existingMedia = media(UUID.randomUUID());
        CollectionItem existing = item(fixture.collection, existingMedia, true, ExternalSource.TMDB, 0);
        when(fixture.items.findByCollectionIdOrderByPositionAsc(fixture.collection.getId())).thenReturn(List.of(existing));
        when(fixture.mediaReferences.findAllByMediaIdIn(anyCollection()))
                .thenReturn(List.of(externalReference(existingMedia, "1")));

        fixture.writer().reconcile(fixture.collection.getId(), "collection-10", snapshot(List.of(
                        new TmdbCollectionSnapshot.Movie("1", "First", null, LocalDate.of(2020, 1, 1), null))),
                List.of(new CollectionSyncWriter.MaterializedMovie("1", existingMedia.getId())), "pt-BR", Instant.now());

        verify(fixture.items, never()).saveAll(org.mockito.ArgumentMatchers.<Iterable<CollectionItem>>any());
        verify(fixture.items, never()).deleteAll(anyCollection());
    }

    @Test
    void addsOnlyTheNewSequelOnTheNextSynchronization() {
        Fixture fixture = new Fixture();
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        UUID thirdId = UUID.randomUUID();
        UUID sequelId = UUID.randomUUID();
        Media first = media(firstId);
        Media second = media(secondId);
        Media third = media(thirdId);
        List<CollectionItem> existing = List.of(
                item(fixture.collection, first, true, ExternalSource.TMDB, 0),
                item(fixture.collection, second, true, ExternalSource.TMDB, 1),
                item(fixture.collection, third, true, ExternalSource.TMDB, 2)
        );
        when(fixture.items.findByCollectionIdOrderByPositionAsc(fixture.collection.getId()))
                .thenReturn(List.of(), existing);
        when(fixture.mediaReferences.findAllByMediaIdIn(anyCollection())).thenReturn(List.of(
                externalReference(first, "1"), externalReference(second, "2"), externalReference(third, "3")));
        when(fixture.mediaRepository.getReferenceById(firstId)).thenReturn(first);
        when(fixture.mediaRepository.getReferenceById(secondId)).thenReturn(second);
        when(fixture.mediaRepository.getReferenceById(thirdId)).thenReturn(third);
        when(fixture.mediaRepository.getReferenceById(sequelId)).thenReturn(media(sequelId));
        CollectionSyncWriter writer = fixture.writer();
        List<CollectionSyncWriter.MaterializedMovie> firstSync = List.of(
                new CollectionSyncWriter.MaterializedMovie("1", firstId),
                new CollectionSyncWriter.MaterializedMovie("2", secondId),
                new CollectionSyncWriter.MaterializedMovie("3", thirdId));

        writer.reconcile(fixture.collection.getId(), "collection-10", snapshot(List.of(
                        new TmdbCollectionSnapshot.Movie("1", "First", null, null, null),
                        new TmdbCollectionSnapshot.Movie("2", "Second", null, null, null),
                        new TmdbCollectionSnapshot.Movie("3", "Third", null, null, null))),
                firstSync, "pt-BR", Instant.parse("2025-01-01T00:00:00Z"));
        writer.reconcile(fixture.collection.getId(), "collection-10", snapshot(List.of(
                        new TmdbCollectionSnapshot.Movie("1", "First", null, null, null),
                        new TmdbCollectionSnapshot.Movie("2", "Second", null, null, null),
                        new TmdbCollectionSnapshot.Movie("3", "Third", null, null, null),
                        new TmdbCollectionSnapshot.Movie("4", "New sequel", null, null, null))),
                List.of(firstSync.get(0), firstSync.get(1), firstSync.get(2),
                        new CollectionSyncWriter.MaterializedMovie("4", sequelId)), "pt-BR",
                Instant.parse("2025-01-02T00:00:00Z"));

        ArgumentCaptor<Iterable<CollectionItem>> savedItems = ArgumentCaptor.forClass(Iterable.class);
        verify(fixture.items, org.mockito.Mockito.times(2)).saveAll(savedItems.capture());
        assertThat(savedItems.getAllValues().get(0)).hasSize(3);
        assertThat(savedItems.getAllValues().get(1)).singleElement()
                .satisfies(item -> assertThat(item.getMedia().getId()).isEqualTo(sequelId));
        verify(fixture.items, never()).deleteAll(anyCollection());
    }

    @Test
    void preservesManualCollectionMetadata() {
        Fixture fixture = new Fixture();
        fixture.collection.setSourceMode(CollectionSourceMode.MANUAL);
        fixture.collection.setTitle("Cabinet title");
        fixture.collection.setDescription("Cabinet description");
        UUID mediaId = UUID.randomUUID();
        when(fixture.mediaRepository.getReferenceById(mediaId)).thenReturn(media(mediaId));

        fixture.writer().reconcile(fixture.collection.getId(), "collection-10", snapshot(List.of()),
                List.of(new CollectionSyncWriter.MaterializedMovie("1", mediaId)), "pt-BR", Instant.now());

        assertThat(fixture.collection.getTitle()).isEqualTo("Cabinet title");
        assertThat(fixture.collection.getDescription()).isEqualTo("Cabinet description");
        assertThat(fixture.collection.getStartDate()).isNull();
    }

    private TmdbCollectionSnapshot snapshot(List<TmdbCollectionSnapshot.Movie> movies) {
        return new TmdbCollectionSnapshot("collection-10", "TMDB Collection", "Remote overview",
                "poster", "backdrop", movies, true);
    }

    private Media media(UUID id) {
        Media media = new Media();
        media.setId(id);
        return media;
    }

    private CollectionItem item(Collection collection, Media media, boolean managed, ExternalSource source, int position) {
        CollectionItem item = new CollectionItem();
        item.setCollection(collection);
        item.setMedia(media);
        item.setPosition(position);
        item.setSourceManaged(managed);
        item.setSourceProvider(source);
        item.setRelationType(FranchiseMediaRelationType.CORE);
        return item;
    }

    private ExternalReference externalReference(Media media, String externalId) {
        ExternalReference reference = new ExternalReference();
        reference.setMedia(media);
        reference.setSource(ExternalSource.TMDB);
        reference.setExternalId(externalId);
        return reference;
    }

    private static class Fixture {
        private final CollectionRepository collections = mock(CollectionRepository.class);
        private final CollectionExternalReferenceRepository collectionReferences = mock(CollectionExternalReferenceRepository.class);
        private final CollectionItemRepository items = mock(CollectionItemRepository.class);
        private final ExternalReferenceRepository mediaReferences = mock(ExternalReferenceRepository.class);
        private final MediaRepository mediaRepository = mock(MediaRepository.class);
        private final CollectionTranslationRepository translations = mock(CollectionTranslationRepository.class);
        private final Collection collection = new Collection();
        private final CollectionExternalReference reference = new CollectionExternalReference();

        private Fixture() {
            collection.setId(UUID.randomUUID());
            collection.setType(CollectionType.FILM_SERIES);
            collection.setSourceMode(CollectionSourceMode.EXTERNAL);
            reference.setCollection(collection);
            reference.setProvider(ExternalSource.TMDB);
            reference.setExternalId("collection-10");
            when(collections.findByIdForUpdate(collection.getId())).thenReturn(Optional.of(collection));
            when(collectionReferences.findByCollectionId(collection.getId())).thenReturn(List.of(reference));
            when(items.findByCollectionIdOrderByPositionAsc(collection.getId())).thenReturn(List.of());
            when(mediaReferences.findAllByMediaIdIn(anyCollection())).thenReturn(List.of());
        }

        private CollectionSyncWriter writer() {
            return new CollectionSyncWriter(collections, collectionReferences, items, mediaReferences, mediaRepository, translations);
        }
    }
}
