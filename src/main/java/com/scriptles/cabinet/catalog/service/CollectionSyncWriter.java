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
import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.external.ExternalMediaException;
import com.scriptles.cabinet.media.external.TmdbCollectionSnapshot;
import com.scriptles.cabinet.media.repository.ExternalReferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CollectionSyncWriter {
    private final CollectionRepository collectionRepository;
    private final CollectionExternalReferenceRepository collectionExternalReferenceRepository;
    private final CollectionItemRepository collectionItemRepository;
    private final ExternalReferenceRepository mediaExternalReferenceRepository;
    private final MediaRepository mediaRepository;

    @Transactional
    public void reconcile(
            UUID collectionId,
            String expectedTmdbCollectionId,
            TmdbCollectionSnapshot snapshot,
            List<MaterializedMovie> movies,
            Instant syncedAt
    ) {
        Collection collection = collectionRepository.findByIdForUpdate(collectionId)
                .orElseThrow(() -> new IllegalArgumentException("Collection not found"));
        if (collection.getType() != CollectionType.FILM_SERIES) {
            throw new ExternalMediaException("Collection type changed while TMDB synchronization was running");
        }
        CollectionExternalReference collectionReference = collectionExternalReferenceRepository
                .findByCollectionId(collectionId).stream()
                .filter(reference -> reference.getProvider() == ExternalSource.TMDB)
                .findFirst()
                .orElseThrow(() -> new ExternalMediaException("TMDB collection reference was removed during sync"));
        if (!expectedTmdbCollectionId.equals(collectionReference.getExternalId())) {
            throw new ExternalMediaException("TMDB collection reference changed while synchronization was running");
        }

        Map<String, Integer> remotePositions = new HashMap<>();
        for (int index = 0; index < movies.size(); index++) {
            remotePositions.put(movies.get(index).externalId(), index);
        }
        List<CollectionItem> existingItems = collectionItemRepository
                .findByCollectionIdOrderByPositionAsc(collectionId);
        Set<UUID> existingMediaIds = existingItems.stream()
                .map(item -> item.getMedia().getId())
                .collect(Collectors.toSet());
        Map<UUID, String> tmdbIdByMediaId = new HashMap<>();
        if (!existingMediaIds.isEmpty()) {
            for (ExternalReference reference : mediaExternalReferenceRepository.findAllByMediaIdIn(existingMediaIds)) {
                if (reference.getSource() == ExternalSource.TMDB) {
                    tmdbIdByMediaId.put(reference.getMedia().getId(), reference.getExternalId());
                }
            }
        }

        Map<UUID, CollectionItem> existingByMediaId = existingItems.stream()
                .collect(Collectors.toMap(item -> item.getMedia().getId(), item -> item));
        List<CollectionItem> changedItems = new ArrayList<>();
        List<CollectionItem> removedItems = new ArrayList<>();

        for (CollectionItem item : existingItems) {
            if (!item.isSourceManaged() || item.getSourceProvider() != ExternalSource.TMDB) {
                continue;
            }
            String tmdbMovieId = tmdbIdByMediaId.get(item.getMedia().getId());
            Integer position = tmdbMovieId == null ? null : remotePositions.get(tmdbMovieId);
            if (position == null) {
                removedItems.add(item);
                continue;
            }
            boolean changed = false;
            if (item.getPosition() != position) {
                item.setPosition(position);
                changed = true;
            }
            if (item.getRelationType() != FranchiseMediaRelationType.CORE) {
                item.setRelationType(FranchiseMediaRelationType.CORE);
                changed = true;
            }
            if (changed) {
                item.setUpdatedAt(syncedAt);
                changedItems.add(item);
            }
        }

        for (MaterializedMovie movie : movies) {
            if (existingByMediaId.containsKey(movie.mediaId())) {
                // Manually managed links remain manually managed even when TMDB also lists the movie.
                continue;
            }
            CollectionItem item = new CollectionItem();
            item.setCollection(collection);
            item.setMedia(mediaRepository.getReferenceById(movie.mediaId()));
            item.setPosition(remotePositions.get(movie.externalId()));
            item.setRelationType(FranchiseMediaRelationType.CORE);
            item.setSourceManaged(true);
            item.setSourceProvider(ExternalSource.TMDB);
            item.setCreatedAt(syncedAt);
            item.setUpdatedAt(syncedAt);
            changedItems.add(item);
        }

        if (!removedItems.isEmpty()) collectionItemRepository.deleteAll(removedItems);
        if (!changedItems.isEmpty()) collectionItemRepository.saveAll(changedItems);

        if (collection.getSourceMode() == CollectionSourceMode.EXTERNAL) {
            collection.setTitle(snapshot.name());
            collection.setDescription(snapshot.overview());
            collection.setPosterUrl(snapshot.posterUrl());
            collection.setBackdropUrl(snapshot.backdropUrl());
            collection.setStartDate(earliestReleaseDate(snapshot));
            collection.setEndDate(latestReleaseDate(snapshot));
        }
        collection.setLastSyncedAt(syncedAt);
        collectionReference.setLastSyncedAt(syncedAt);
        collectionRepository.save(collection);
        collectionExternalReferenceRepository.save(collectionReference);
    }

    private LocalDate earliestReleaseDate(TmdbCollectionSnapshot snapshot) {
        return snapshot.movies().stream()
                .map(TmdbCollectionSnapshot.Movie::releaseDate)
                .filter(date -> date != null)
                .min(LocalDate::compareTo)
                .orElse(null);
    }

    private LocalDate latestReleaseDate(TmdbCollectionSnapshot snapshot) {
        return snapshot.movies().stream()
                .map(TmdbCollectionSnapshot.Movie::releaseDate)
                .filter(date -> date != null)
                .max(LocalDate::compareTo)
                .orElse(null);
    }

    public record MaterializedMovie(String externalId, UUID mediaId) {
    }
}
