package com.scriptles.cabinet.catalog.service;

import com.scriptles.cabinet.catalog.api.CollectionAuditResponse;
import com.scriptles.cabinet.catalog.entity.Collection;
import com.scriptles.cabinet.catalog.entity.CollectionExternalReference;
import com.scriptles.cabinet.catalog.entity.CollectionSourceItem;
import com.scriptles.cabinet.catalog.entity.CollectionSourceSnapshot;
import com.scriptles.cabinet.catalog.repository.CollectionExternalReferenceRepository;
import com.scriptles.cabinet.catalog.repository.CollectionRepository;
import com.scriptles.cabinet.catalog.repository.CollectionSourceItemRepository;
import com.scriptles.cabinet.catalog.repository.CollectionSourceSnapshotRepository;
import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.repository.MediaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CollectionAuditModerationService {
    private static final List<String> FIELD_NAMES = List.of(
            "TITLE", "ORIGINAL_TITLE", "DESCRIPTION", "POSTER_URL", "BACKDROP_URL", "START_DATE", "END_DATE");

    private final CollectionRepository collections;
    private final CollectionExternalReferenceRepository references;
    private final CollectionSourceSnapshotRepository snapshots;
    private final CollectionSourceItemRepository sourceItems;
    private final MediaRepository mediaRepository;
    private final JdbcTemplate jdbcTemplate;

    public CollectionAuditResponse audit(UUID collectionId) {
        Collection collection = collections.findById(collectionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COLLECTION_NOT_FOUND", "Collection was not found"));
        CollectionExternalReference reference = references.findByCollectionId(collectionId).stream()
                .filter(item -> item.getProvider() == ExternalSource.TMDB).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TMDB_COLLECTION_REFERENCE_REQUIRED",
                        "Collection does not have a TMDB reference"));
        CollectionSourceSnapshot snapshot = snapshots.findByCollectionIdAndProvider(collectionId, "TMDB").orElse(null);
        List<CollectionSourceItem> manifestRows = sourceItems.findByCollectionIdAndProvider(collectionId, "TMDB");
        Set<UUID> mediaIds = manifestRows.stream().map(CollectionSourceItem::getResolvedMediaId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> mediaTitles = new HashMap<>();
        mediaRepository.findAllById(mediaIds).forEach(media -> mediaTitles.put(media.getId(), media.getTitle()));
        Map<String, String> owners = jdbcTemplate.query(
                "select field_name, owner from collection_field_ownership where collection_id = ?",
                statement -> statement.setObject(1, collectionId), result -> {
                    Map<String, String> values = new HashMap<>();
                    while (result.next()) values.put(result.getString(1), result.getString(2));
                    return values;
                });

        List<CollectionAuditResponse.FieldComparison> fields = fieldComparisons(collection, snapshot, owners);
        long resolved = manifestRows.stream().filter(item -> item.isSourcePresent()
                && "RESOLVED".equals(item.getResolutionStatus())).count();
        long queued = manifestRows.stream().filter(item -> item.isSourcePresent()
                && List.of("QUEUED", "RESOLVING", "UNRESOLVED").contains(item.getResolutionStatus())).count();
        long failed = manifestRows.stream().filter(item -> item.isSourcePresent()
                && "FAILED".equals(item.getResolutionStatus())).count();
        long removed = manifestRows.stream().filter(item -> !item.isSourcePresent()).count();
        long activeTotal = manifestRows.stream().filter(CollectionSourceItem::isSourcePresent).count();
        List<CollectionAuditResponse.ManifestItem> items = manifestRows.stream()
                .sorted(Comparator.comparingInt(CollectionSourceItem::getPosition))
                .map(item -> new CollectionAuditResponse.ManifestItem(item.getExternalId(), item.getTitle(),
                        item.getResolvedMediaId(), mediaTitles.get(item.getResolvedMediaId()), item.getPosition(),
                        item.getResolutionStatus(), item.isSourcePresent(), item.getReleaseDate(),
                        item.getFirstSeenAt(), item.getLastSeenAt(), item.getRemovedAt(), item.getLastMaterializationError()))
                .toList();
        return new CollectionAuditResponse(collectionId, collection.getTitle(), "TMDB", reference.getExternalId(),
                new CollectionAuditResponse.Sync(reference.getSyncStatus(), reference.getSyncPriority(),
                        reference.getLastSyncAttemptAt(), reference.getLastSuccessfulSyncAt(), reference.getNextSyncAt(),
                        reference.getLastError(), reference.getLastOperationId()),
                snapshot == null ? null : new CollectionAuditResponse.SourceSnapshot(snapshot.getContentHash(),
                        snapshot.getRemoteItemCount(), snapshot.getFetchedAt()),
                new CollectionAuditResponse.Manifest(activeTotal, resolved, queued, failed, removed),
                fields, items);
    }

    private List<CollectionAuditResponse.FieldComparison> fieldComparisons(Collection collection,
            CollectionSourceSnapshot snapshot, Map<String, String> owners) {
        if (snapshot == null) return List.of();
        Map<String, String> source = new LinkedHashMap<>();
        source.put("TITLE", snapshot.getName());
        source.put("ORIGINAL_TITLE", snapshot.getName());
        source.put("DESCRIPTION", snapshot.getOverview());
        source.put("POSTER_URL", snapshot.getPosterUrl());
        source.put("BACKDROP_URL", snapshot.getBackdropUrl());
        Map<String, String> effective = new LinkedHashMap<>();
        effective.put("TITLE", collection.getTitle());
        effective.put("ORIGINAL_TITLE", collection.getOriginalTitle());
        effective.put("DESCRIPTION", collection.getDescription());
        effective.put("POSTER_URL", collection.getPosterUrl());
        effective.put("BACKDROP_URL", collection.getBackdropUrl());
        effective.put("START_DATE", Objects.toString(collection.getStartDate(), null));
        effective.put("END_DATE", Objects.toString(collection.getEndDate(), null));
        List<LocalDate> releaseDates = sourceItems.findByCollectionIdAndProviderAndSourcePresentTrueOrderByPositionAsc(
                        collection.getId(), "TMDB").stream()
                .map(CollectionSourceItem::getReleaseDate).filter(Objects::nonNull).toList();
        source.put("START_DATE", releaseDates.stream().min(LocalDate::compareTo).map(Object::toString).orElse(null));
        source.put("END_DATE", releaseDates.stream().max(LocalDate::compareTo).map(Object::toString).orElse(null));
        return FIELD_NAMES.stream().map(field -> new CollectionAuditResponse.FieldComparison(field,
                source.get(field), effective.get(field), owners.getOrDefault(field, "SOURCE"))).toList();
    }
}
