package com.scriptles.cabinet.catalog.service;

import com.scriptles.cabinet.catalog.api.*;
import com.scriptles.cabinet.catalog.collection.CollectionType;
import com.scriptles.cabinet.catalog.domain.CatalogEntityStatus;
import com.scriptles.cabinet.catalog.entity.*;
import com.scriptles.cabinet.catalog.event.TmdbCollectionReferenceLinkedEvent;
import com.scriptles.cabinet.catalog.franchise.FranchiseMediaRelationType;
import com.scriptles.cabinet.catalog.repository.*;
import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.repository.MediaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class CatalogModerationService {
    private static final Set<String> SOURCE_FIELDS = Set.of(
            "TITLE", "ORIGINAL_TITLE", "DESCRIPTION", "POSTER_URL", "BACKDROP_URL", "START_DATE", "END_DATE");

    private final CollectionRepository collectionRepository;
    private final FranchiseRepository franchiseRepository;
    private final CollectionItemRepository itemRepository;
    private final FranchiseCollectionRepository franchiseCollectionRepository;
    private final FranchiseMediaRepository franchiseMediaRepository;
    private final CollectionExternalReferenceRepository collectionExternalReferenceRepository;
    private final MediaRepository mediaRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final JdbcTemplate jdbcTemplate;
    private final CollectionSourceSnapshotRepository sourceSnapshots;
    private final CollectionSourceItemRepository sourceItems;
    private final CacheManager cacheManager;

    public Collection saveCollection(UUID id, UpsertCollectionRequest request) {
        Collection collection = id == null ? new Collection()
                : collectionRepository.findById(id).orElseThrow(() -> notFound("COLLECTION_NOT_FOUND"));
        if (id == null && collectionRepository.findBySlug(request.slug()).isPresent()) throw conflict();
        if (id != null) {
            markChangedFieldAsCurated(collection, "TITLE", collection.getTitle(), request.title());
            markChangedFieldAsCurated(collection, "ORIGINAL_TITLE", collection.getOriginalTitle(), request.originalTitle());
            markChangedFieldAsCurated(collection, "DESCRIPTION", collection.getDescription(), request.description());
            markChangedFieldAsCurated(collection, "POSTER_URL", collection.getPosterUrl(), request.posterUrl());
            markChangedFieldAsCurated(collection, "BACKDROP_URL", collection.getBackdropUrl(), request.backdropUrl());
        }
        collection.setSlug(request.slug());
        collection.setTitle(request.title());
        collection.setOriginalTitle(request.originalTitle());
        collection.setDescription(request.description());
        collection.setType(request.type());
        collection.setSourceMode(request.sourceMode());
        collection.setStatus(request.status() == null ? CatalogEntityStatus.ACTIVE : request.status());
        collection.setPosterUrl(request.posterUrl());
        collection.setBackdropUrl(request.backdropUrl());
        return collectionRepository.save(collection);
    }

    public void resetCollectionFieldToSource(UUID collectionId, String fieldName) {
        String normalized = fieldName == null ? "" : fieldName.trim().toUpperCase(java.util.Locale.ROOT);
        if (!SOURCE_FIELDS.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "COLLECTION_FIELD_UNSUPPORTED",
                    "This collection field cannot be reset to its source");
        }
        if (!collectionRepository.existsById(collectionId)) throw notFound("COLLECTION_NOT_FOUND");
        jdbcTemplate.update("delete from collection_field_ownership where collection_id = ? and field_name = ?",
                collectionId, normalized);
        CollectionSourceSnapshot snapshot = sourceSnapshots.findByCollectionIdAndProvider(collectionId, "TMDB")
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "COLLECTION_SOURCE_NOT_AVAILABLE",
                        "There is no stored TMDB snapshot to restore from"));
        Collection collection = collectionRepository.findByIdForUpdate(collectionId)
                .orElseThrow(() -> notFound("COLLECTION_NOT_FOUND"));
        switch (normalized) {
            case "TITLE" -> collection.setTitle(snapshot.getName());
            case "ORIGINAL_TITLE" -> collection.setOriginalTitle(snapshot.getName());
            case "DESCRIPTION" -> collection.setDescription(snapshot.getOverview());
            case "POSTER_URL" -> collection.setPosterUrl(snapshot.getPosterUrl());
            case "BACKDROP_URL" -> collection.setBackdropUrl(snapshot.getBackdropUrl());
            case "START_DATE" -> collection.setStartDate(sourceDate(collectionId, true));
            case "END_DATE" -> collection.setEndDate(sourceDate(collectionId, false));
            default -> throw new IllegalStateException("Validated collection field was not handled");
        }
        collectionRepository.save(collection);
        Cache cache = cacheManager.getCache("collectionDetails");
        if (cache != null) cache.evict(collectionId);
    }

    public Franchise saveFranchise(UUID id, UpsertFranchiseRequest request) {
        Franchise franchise = id == null ? new Franchise()
                : franchiseRepository.findById(id).orElseThrow(() -> notFound("FRANCHISE_NOT_FOUND"));
        if (id == null && franchiseRepository.findBySlug(request.slug()).isPresent()) throw conflict();
        if (request.parentId() != null && id != null && request.parentId().equals(id)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FRANCHISE_CYCLE", "A franquia não pode ser pai de si mesma");
        }
        Franchise parent = request.parentId() == null ? null : franchiseRepository.findById(request.parentId())
                .orElseThrow(() -> notFound("PARENT_NOT_FOUND"));
        if (parent != null) {
            Franchise cursor = parent;
            while (cursor != null) {
                if (id != null && id.equals(cursor.getId())) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "FRANCHISE_CYCLE", "A hierarquia contém um ciclo");
                }
                cursor = cursor.getParent();
            }
        }
        franchise.setSlug(request.slug());
        franchise.setName(request.name());
        franchise.setOriginalName(request.originalName());
        franchise.setDescription(request.description());
        franchise.setType(request.type());
        franchise.setStatus(request.status() == null ? CatalogEntityStatus.ACTIVE : request.status());
        franchise.setPosterUrl(request.posterUrl());
        franchise.setBackdropUrl(request.backdropUrl());
        franchise.setParent(parent);
        return franchiseRepository.save(franchise);
    }

    private void markChangedFieldAsCurated(Collection collection, String field, Object before, Object after) {
        if (java.util.Objects.equals(before, after)) return;
        jdbcTemplate.update("""
                insert into collection_field_ownership(collection_id, field_name, owner, updated_at)
                values (?, ?, 'CURATOR', ?)
                on conflict (collection_id, field_name) do update
                set owner = 'CURATOR', updated_at = excluded.updated_at
                """, collection.getId(), field, Instant.now());
    }

    private LocalDate sourceDate(UUID collectionId, boolean earliest) {
        return sourceItems.findByCollectionIdAndProviderAndSourcePresentTrueOrderByPositionAsc(collectionId, "TMDB")
                .stream().map(CollectionSourceItem::getReleaseDate).filter(java.util.Objects::nonNull)
                .reduce((left, right) -> earliest
                        ? (left.isBefore(right) ? left : right)
                        : (left.isAfter(right) ? left : right)).orElse(null);
    }

    private ApiException notFound(String code) {
        return new ApiException(HttpStatus.NOT_FOUND, code, "Entidade não encontrada");
    }

    private ApiException conflict() {
        return new ApiException(HttpStatus.CONFLICT, "SLUG_ALREADY_EXISTS", "Slug já existe");
    }

    private ApiException externalReferenceConflict() {
        return new ApiException(HttpStatus.CONFLICT, "COLLECTION_EXTERNAL_REFERENCE_ALREADY_EXISTS",
                "TMDB collection is already linked to another collection");
    }

    public void setTmdbCollectionReference(UUID collectionId, SetTmdbCollectionReferenceRequest request) {
        Collection collection = collectionRepository.findById(collectionId)
                .orElseThrow(() -> notFound("COLLECTION_NOT_FOUND"));
        if (collection.getType() != CollectionType.FILM_SERIES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "COLLECTION_TYPE_UNSUPPORTED",
                    "Only film series can reference a TMDB collection");
        }
        collectionExternalReferenceRepository.findByProviderAndExternalId(ExternalSource.TMDB, request.externalId())
                .filter(existing -> !existing.getCollection().getId().equals(collectionId))
                .ifPresent(existing -> { throw externalReferenceConflict(); });
        CollectionExternalReference reference = collectionExternalReferenceRepository.findByCollectionId(collectionId)
                .stream().filter(existing -> existing.getProvider() == ExternalSource.TMDB)
                .findFirst().orElseGet(CollectionExternalReference::new);
        reference.setCollection(collection);
        reference.setProvider(ExternalSource.TMDB);
        reference.setExternalId(request.externalId());
        reference.setExternalUrl("https://www.themoviedb.org/collection/" + request.externalId());
        collection.setSourceMode(com.scriptles.cabinet.catalog.collection.CollectionSourceMode.EXTERNAL);
        collectionRepository.save(collection);
        collectionExternalReferenceRepository.save(reference);
        eventPublisher.publishEvent(new TmdbCollectionReferenceLinkedEvent(collectionId));
    }

    public void linkMedia(UUID collectionId, LinkMediaRequest request) {
        Collection collection = collectionRepository.findById(collectionId)
                .orElseThrow(() -> notFound("COLLECTION_NOT_FOUND"));
        Media media = mediaRepository.findById(request.mediaId()).orElseThrow(() -> notFound("MEDIA_NOT_FOUND"));
        CollectionItem item = itemRepository.findByCollectionIdAndMediaId(collectionId, request.mediaId())
                .orElseGet(CollectionItem::new);
        item.setCollection(collection);
        item.setMedia(media);
        item.setPosition(request.position() == null ? 0 : request.position());
        item.setRelationType(request.relationType() == null ? FranchiseMediaRelationType.CORE : request.relationType());
        item.setSourceManaged(Boolean.TRUE.equals(request.sourceManaged()));
        item.setSourceProvider(request.sourceProvider());
        itemRepository.save(item);
    }

    public void linkCollection(UUID franchiseId, LinkCollectionRequest request) {
        Franchise franchise = franchiseRepository.findById(franchiseId)
                .orElseThrow(() -> notFound("FRANCHISE_NOT_FOUND"));
        Collection collection = collectionRepository.findById(request.collectionId())
                .orElseThrow(() -> notFound("COLLECTION_NOT_FOUND"));
        FranchiseCollection item = franchiseCollectionRepository
                .findById(new FranchiseCollectionId(franchiseId, request.collectionId()))
                .orElseGet(FranchiseCollection::new);
        item.setFranchise(franchise);
        item.setCollection(collection);
        item.setPosition(request.position() == null ? 0 : request.position());
        item.setRelationType(request.relationType() == null ? FranchiseMediaRelationType.CORE : request.relationType());
        item.setSourceManaged(Boolean.TRUE.equals(request.sourceManaged()));
        item.setSourceProvider(request.sourceProvider());
        franchiseCollectionRepository.save(item);
    }

    public void hideCollection(UUID id) {
        Collection collection = collectionRepository.findById(id).orElseThrow(() -> notFound("COLLECTION_NOT_FOUND"));
        collection.setStatus(CatalogEntityStatus.HIDDEN);
    }

    public void hideFranchise(UUID id) {
        Franchise franchise = franchiseRepository.findById(id).orElseThrow(() -> notFound("FRANCHISE_NOT_FOUND"));
        franchise.setStatus(CatalogEntityStatus.HIDDEN);
    }

    public PageResponse<CollectionSummaryResponse> collections(String query, int page, int size) {
        return PageResponse.from(collectionRepository.findPageByTitleContainingIgnoreCase(
                query == null ? "" : query, PageRequest.of(page, size)).map(collection ->
                new CollectionSummaryResponse(collection.getId(), collection.getSlug(), collection.getTitle(),
                        collection.getType().name(), null, null)));
    }

    public PageResponse<FranchiseSummaryResponse> franchises(String query, int page, int size) {
        return PageResponse.from(franchiseRepository.findPageByNameContainingIgnoreCase(
                query == null ? "" : query, PageRequest.of(page, size)).map(franchise ->
                new FranchiseSummaryResponse(franchise.getId(), franchise.getSlug(), franchise.getName(),
                        franchise.getType().name())));
    }
}
