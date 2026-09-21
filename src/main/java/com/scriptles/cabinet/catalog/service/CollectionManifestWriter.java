package com.scriptles.cabinet.catalog.service;

import tools.jackson.databind.ObjectMapper;
import com.scriptles.cabinet.catalog.collection.CollectionSourceMode;
import com.scriptles.cabinet.catalog.collection.CollectionType;
import com.scriptles.cabinet.catalog.domain.CatalogEntityStatus;
import com.scriptles.cabinet.catalog.entity.*;
import com.scriptles.cabinet.catalog.franchise.FranchiseMediaRelationType;
import com.scriptles.cabinet.catalog.repository.*;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.repository.MediaRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static com.scriptles.cabinet.catalog.service.CatalogJobTypes.*;

@Service
@RequiredArgsConstructor
public class CollectionManifestWriter {
    private static final List<String> ACTIVE = List.of(PENDING, PROCESSING, RETRY);

    private final CollectionRepository collections;
    private final CollectionExternalReferenceRepository references;
    private final CollectionSourceSnapshotRepository snapshots;
    private final CollectionSourceItemRepository sourceItems;
    private final CollectionItemRepository collectionItems;
    private final CatalogJobRepository jobs;
    private final CatalogOperationRepository operations;
    private final CatalogOperationEventRepository events;
    private final MediaRepository mediaRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final CacheManager cacheManager;
    private final MeterRegistry meters;

    @Value("${catalog.collections.sync.hot-after:6h}")
    private Duration hotAfter;
    @Value("${catalog.collections.sync.warm-after:24h}")
    private Duration warmAfter;
    @Value("${catalog.collections.sync.cold-after:7d}")
    private Duration coldAfter;

    @Transactional
    public HydrationResult persist(UUID operationId, CatalogJob job, String locale,
                                    com.scriptles.cabinet.media.external.TmdbCollectionSnapshot snapshot) {
        Instant now = Instant.now();
        CollectionExternalReference reference = references
                .findByProviderAndExternalId(ExternalSource.TMDB, snapshot.externalId())
                .orElse(null);
        Collection collection = reference == null ? createCollection(snapshot, now) : reference.getCollection();
        if (reference == null) {
            reference = new CollectionExternalReference();
            reference.setCollection(collection);
            reference.setProvider(ExternalSource.TMDB);
            reference.setExternalId(snapshot.externalId());
            reference.setExternalUrl("https://www.themoviedb.org/collection/" + snapshot.externalId());
            references.save(reference);
        }

        List<com.scriptles.cabinet.media.external.TmdbCollectionSnapshot.Movie> movies = distinctMovies(snapshot.movies());
        String hash = sha256(snapshot);
        CollectionSourceSnapshot storedSnapshot = snapshots.findByCollectionIdAndProvider(collection.getId(), "TMDB")
                .orElse(null);
        boolean noChange = storedSnapshot != null && hash.equals(storedSnapshot.getContentHash());
        boolean collectionFieldsChanged = applySourceMetadata(collection, snapshot, movies);

        if (!noChange) {
            reconcileItems(collection, operationId, job, locale, movies, now);
            upsertSnapshot(collection, snapshot, hash, movies.size(), now);
            // The additive public sourceItems view changes whenever the source manifest changes.
            if (collectionFieldsChanged || storedSnapshot == null || !noChange) evictCollection(collection.getId());
            if (storedSnapshot != null) {
                meters.counter("cabinet.catalog.collections.changed", "provider", "TMDB").increment();
            }
            event(operationId, job.getId(), storedSnapshot == null ? "COLLECTION_CREATED" : "COLLECTION_SOURCE_CHANGED",
                    "INFO", storedSnapshot == null ? "Cabinet collection created from TMDB" : "TMDB collection source changed",
                    "COLLECTION", collection.getId(), snapshot.externalId(), Map.of("sourceItems", movies.size()), now);
        } else {
            if (collectionFieldsChanged) evictCollection(collection.getId());
            // A no-op source hash must still repair unresolved/failed rows without rewriting the manifest.
            sourceItems.findByCollectionIdAndProviderAndSourcePresentTrueOrderByPositionAsc(
                    collection.getId(), "TMDB").stream()
                    .filter(item -> !"RESOLVED".equals(item.getResolutionStatus()))
                    .forEach(item -> {
                        item.setResolutionStatus("QUEUED");
                        item.setLastMaterializationError(null);
                        item.setUpdatedAt(now);
                        enqueueMaterialization(operationId, job, collection.getId(), item.getExternalId(), locale, now);
                    });
            storedSnapshot.setFetchedAt(now);
            storedSnapshot.setRemoteItemCount(movies.size());
            storedSnapshot.setUpdatedAt(now);
            snapshots.save(storedSnapshot);
            meters.counter("cabinet.catalog.collections.noop", "provider", "TMDB").increment();
            event(operationId, job.getId(), "COLLECTION_NO_CHANGE", "INFO",
                    "TMDB collection snapshot is unchanged", "COLLECTION", collection.getId(),
                    snapshot.externalId(), Map.of("sourceItems", movies.size()), now);
        }

        reference.setLastSyncedAt(now);
        reference.setLastSuccessfulSyncAt(now);
        reference.setLastSyncAttemptAt(now);
        reference.setSyncStatus("READY");
        reference.setSyncPriority("MANUAL".equals(job.getTrigger()) ? "HOT" : reference.getSyncPriority());
        reference.setNextSyncAt(now.plus(syncDelay(reference.getSyncPriority())));
        reference.setLastError(null);
        reference.setLastOperationId(operationId);
        references.save(reference);
        collection.setLastSyncedAt(now);
        collections.save(collection);

        long unresolved = sourceItems.findByCollectionIdAndProviderAndSourcePresentTrueOrderByPositionAsc(
                collection.getId(), "TMDB").stream()
                .filter(item -> !"RESOLVED".equals(item.getResolutionStatus())).count();
        operations.findById(operationId).ifPresent(operation -> {
            operation.setRootCollectionId(collection.getId());
            operation.setTotalItems(movies.size());
            operation.setUpdatedAt(now);
        });
        meters.counter("cabinet.catalog.collections.hydrated", "provider", "TMDB").increment();
        return new HydrationResult(collection.getId(), movies.size(), noChange ? movies.size() : 0,
                unresolved, !noChange);
    }

    private int reconcileItems(Collection collection, UUID operationId, CatalogJob parentJob, String locale,
            List<com.scriptles.cabinet.media.external.TmdbCollectionSnapshot.Movie> movies, Instant now) {
        List<CollectionSourceItem> existing = sourceItems.findByCollectionIdAndProvider(collection.getId(), "TMDB");
        Map<String, CollectionSourceItem> byExternalId = new HashMap<>();
        existing.forEach(item -> byExternalId.put(item.getExternalId(), item));
        Set<String> incoming = new HashSet<>();
        for (var movie : movies) incoming.add(movie.externalId());
        for (CollectionSourceItem item : existing) {
            if (!incoming.contains(item.getExternalId()) && item.isSourcePresent()) {
                item.setSourcePresent(false);
                item.setRemovedAt(now);
                item.setUpdatedAt(now);
                if (item.getResolvedMediaId() != null) {
                    collectionItems.findByCollectionIdAndMediaId(collection.getId(), item.getResolvedMediaId())
                            .filter(CollectionItem::isSourceManaged)
                            .filter(link -> link.getSourceProvider() == ExternalSource.TMDB)
                            .ifPresent(link -> collectionItems.delete(link));
                    evictMedia(item.getResolvedMediaId());
                }
                event(operationId, parentJob.getId(), "MANIFEST_ITEM_REMOVED", "WARNING",
                        "TMDB removed a collection item", "MOVIE", item.getResolvedMediaId(), item.getExternalId(),
                        Map.of("position", item.getPosition()), now);
            }
        }

        int added = 0;
        for (int position = 0; position < movies.size(); position++) {
            var movie = movies.get(position);
            CollectionSourceItem item = byExternalId.get(movie.externalId());
            String contentHash = sha256(movie);
            if (item == null) {
                item = new CollectionSourceItem();
                item.setId(UUID.randomUUID());
                item.setCollectionId(collection.getId());
                item.setProvider("TMDB");
                item.setExternalId(movie.externalId());
                item.setExternalMediaType("MOVIE");
                item.setResolutionStatus("UNRESOLVED");
                item.setFirstSeenAt(now);
                item.setCreatedAt(now);
                added++;
                event(operationId, parentJob.getId(), "MANIFEST_ITEM_ADDED", "INFO",
                        "TMDB collection item discovered", "MOVIE", null, movie.externalId(),
                        Map.of("position", position), now);
            } else if (item.isSourcePresent() && item.getPosition() != position) {
                event(operationId, parentJob.getId(), "MANIFEST_ITEM_MOVED", "INFO",
                        "TMDB collection item order changed", "MOVIE", item.getResolvedMediaId(), movie.externalId(),
                        Map.of("from", item.getPosition(), "to", position), now);
            }
            item.setPosition(position);
            item.setTitle(movie.title());
            item.setOriginalTitle(movie.originalTitle());
            item.setReleaseDate(movie.releaseDate());
            item.setPosterUrl(movie.posterUrl());
            item.setBackdropUrl(movie.backdropUrl());
            item.setOriginalLanguage(movie.originalLanguage());
            item.setContentHash(contentHash);
            item.setSourcePresent(true);
            item.setLastSeenAt(now);
            item.setRemovedAt(null);
            item.setUpdatedAt(now);
            if (!"RESOLVED".equals(item.getResolutionStatus())) {
                item.setResolutionStatus("QUEUED");
                item.setLastMaterializationError(null);
                enqueueMaterialization(operationId, parentJob, collection.getId(), movie.externalId(), locale, now);
            }
            sourceItems.save(item);
        }
        return added;
    }

    private void enqueueMaterialization(UUID operationId, CatalogJob parent, UUID collectionId,
            String externalId, String locale, Instant now) {
        String dedupe = "COLLECTION_ITEM_MATERIALIZE:TMDB:MOVIE:" + externalId + ":COLLECTION:" + collectionId;
        if (jobs.existsByDeduplicationKeyAndStatusIn(dedupe, ACTIVE)) return;
        CatalogJob job = new CatalogJob();
        job.setId(UUID.randomUUID());
        job.setOperationId(operationId);
        job.setJobType(COLLECTION_ITEM_MATERIALIZE);
        job.setProvider("TMDB");
        job.setEntityType("MOVIE");
        job.setExternalId(externalId);
        job.setCollectionId(collectionId);
        job.setPriority(parent.getPriority());
        job.setTrigger(parent.getTrigger());
        job.setStatus(PENDING);
        job.setDeduplicationKey(dedupe);
        job.setPayload(Map.of("locale", locale));
        job.setAvailableAt(now);
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        jobs.save(job);
        event(operationId, job.getId(), "MATERIALIZATION_QUEUED", "INFO",
                "Movie materialization queued from collection manifest", "MOVIE", null, externalId, Map.of(), now);
        meters.counter("cabinet.catalog.jobs.created", "type", COLLECTION_ITEM_MATERIALIZE).increment();
    }

    private boolean applySourceMetadata(Collection collection,
            com.scriptles.cabinet.media.external.TmdbCollectionSnapshot snapshot,
            List<com.scriptles.cabinet.media.external.TmdbCollectionSnapshot.Movie> movies) {
        boolean changed = false;
        changed |= apply(collection, "TITLE", collection.getTitle(), snapshot.name(), collection::setTitle);
        changed |= apply(collection, "ORIGINAL_TITLE", collection.getOriginalTitle(), snapshot.name(), collection::setOriginalTitle);
        changed |= apply(collection, "DESCRIPTION", collection.getDescription(), snapshot.overview(), collection::setDescription);
        changed |= apply(collection, "POSTER_URL", collection.getPosterUrl(), snapshot.posterUrl(), collection::setPosterUrl);
        changed |= apply(collection, "BACKDROP_URL", collection.getBackdropUrl(), snapshot.backdropUrl(), collection::setBackdropUrl);
        LocalDate start = movies.stream().map(com.scriptles.cabinet.media.external.TmdbCollectionSnapshot.Movie::releaseDate)
                .filter(Objects::nonNull).min(LocalDate::compareTo).orElse(null);
        LocalDate end = movies.stream().map(com.scriptles.cabinet.media.external.TmdbCollectionSnapshot.Movie::releaseDate)
                .filter(Objects::nonNull).max(LocalDate::compareTo).orElse(null);
        changed |= apply(collection, "START_DATE", collection.getStartDate(), start, collection::setStartDate);
        changed |= apply(collection, "END_DATE", collection.getEndDate(), end, collection::setEndDate);
        return changed;
    }

    private <T> boolean apply(Collection collection, String field, T current, T source, java.util.function.Consumer<T> setter) {
        List<String> owner = jdbcTemplate.queryForList(
                "select owner from collection_field_ownership where collection_id = ? and field_name = ?",
                String.class, collection.getId(), field);
        if (!owner.isEmpty() && "CURATOR".equals(owner.getFirst())) return false;
        if (Objects.equals(current, source)) return false;
        setter.accept(source);
        return true;
    }

    private Collection createCollection(com.scriptles.cabinet.media.external.TmdbCollectionSnapshot snapshot,
                                        Instant now) {
        Collection collection = new Collection();
        String base = "tmdb-collection-" + snapshot.externalId();
        String slug = base;
        int suffix = 2;
        while (collections.findBySlug(slug).isPresent()) slug = base + "-" + suffix++;
        collection.setSlug(slug);
        collection.setTitle(snapshot.name());
        collection.setOriginalTitle(snapshot.name());
        collection.setType(CollectionType.FILM_SERIES);
        collection.setSourceMode(CollectionSourceMode.EXTERNAL);
        collection.setStatus(CatalogEntityStatus.ACTIVE);
        collection.setPosterUrl(snapshot.posterUrl());
        collection.setBackdropUrl(snapshot.backdropUrl());
        collection.setLastSyncedAt(now);
        return collections.save(collection);
    }

    private void upsertSnapshot(Collection collection,
            com.scriptles.cabinet.media.external.TmdbCollectionSnapshot snapshot, String hash, int count, Instant now) {
        CollectionSourceSnapshot stored = snapshots.findByCollectionIdAndProvider(collection.getId(), "TMDB")
                .orElseGet(() -> {
                    CollectionSourceSnapshot created = new CollectionSourceSnapshot();
                    created.setId(UUID.randomUUID());
                    created.setCollectionId(collection.getId());
                    created.setProvider("TMDB");
                    created.setExternalId(snapshot.externalId());
                    created.setCreatedAt(now);
                    return created;
                });
        stored.setContentHash(hash);
        stored.setName(snapshot.name());
        stored.setOverview(snapshot.overview());
        stored.setPosterUrl(snapshot.posterUrl());
        stored.setBackdropUrl(snapshot.backdropUrl());
        stored.setRemoteItemCount(count);
        stored.setFetchedAt(now);
        stored.setUpdatedAt(now);
        snapshots.save(stored);
    }

    private List<com.scriptles.cabinet.media.external.TmdbCollectionSnapshot.Movie> distinctMovies(
            List<com.scriptles.cabinet.media.external.TmdbCollectionSnapshot.Movie> movies) {
        LinkedHashMap<String, com.scriptles.cabinet.media.external.TmdbCollectionSnapshot.Movie> distinct = new LinkedHashMap<>();
        movies.forEach(movie -> distinct.putIfAbsent(movie.externalId(), movie));
        return List.copyOf(distinct.values());
    }

    private Duration syncDelay(String tier) {
        return switch (tier == null ? "WARM" : tier) {
            case "HOT" -> hotAfter;
            case "COLD" -> coldAfter;
            default -> warmAfter;
        };
    }

    private String sha256(Object value) {
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(value);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return HexFormat.of().formatHex(digest);
        } catch (RuntimeException | java.security.NoSuchAlgorithmException failure) {
            throw new IllegalStateException("Unable to fingerprint TMDB collection", failure);
        }
    }

    private void event(UUID operationId, UUID jobId, String type, String severity, String message,
                       String entityType, UUID entityId, String externalId, Map<String, Object> metadata, Instant now) {
        CatalogOperationEvent event = new CatalogOperationEvent();
        event.setId(UUID.randomUUID());
        event.setOperationId(operationId);
        event.setJobId(jobId);
        event.setEventType(type);
        event.setSeverity(severity);
        event.setMessage(message);
        event.setEntityType(entityType);
        event.setEntityId(entityId);
        event.setExternalId(externalId);
        event.setMetadata(metadata);
        event.setOccurredAt(now);
        events.save(event);
    }

    private void evictCollection(UUID id) {
        evictAfterCommit("collectionDetails", id);
    }

    private void evictMedia(UUID id) {
        evictAfterCommit("mediaDetails", id + ":pt-BR");
        evictAfterCommit("mediaDetails", id + ":en-US");
    }

    private void evictAfterCommit(String cacheName, Object key) {
        Runnable eviction = () -> {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) cache.evict(key);
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            eviction.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                eviction.run();
            }
        });
    }

    public record HydrationResult(UUID collectionId, long itemCount, long unchangedCount,
                                  long unresolvedCount, boolean changed) {
        public Map<String, Object> metrics() {
            return Map.of("totalItems", itemCount, "unchangedItems", unchangedCount,
                    "updatedItems", changed ? 1 : 0, "processedItems", 0);
        }
    }

    @Transactional(readOnly = true)
    public com.scriptles.cabinet.media.external.TmdbCollectionSnapshot.Movie seed(UUID collectionId, String externalId) {
        CollectionSourceItem item = sourceItems.findByCollectionIdAndProviderAndExternalId(
                        collectionId, "TMDB", externalId)
                .filter(CollectionSourceItem::isSourcePresent)
                .orElseThrow(() -> new IllegalArgumentException("Collection source item is no longer present"));
        return new com.scriptles.cabinet.media.external.TmdbCollectionSnapshot.Movie(
                item.getExternalId(), item.getTitle(), item.getOriginalTitle(), item.getReleaseDate(),
                item.getPosterUrl(), item.getBackdropUrl(), item.getOriginalLanguage());
    }
}
