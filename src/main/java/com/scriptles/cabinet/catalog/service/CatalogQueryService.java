package com.scriptles.cabinet.catalog.service;

import com.scriptles.cabinet.catalog.api.*;
import com.scriptles.cabinet.catalog.collection.CollectionType;
import com.scriptles.cabinet.catalog.domain.CatalogEntityStatus;
import com.scriptles.cabinet.catalog.entity.*;
import com.scriptles.cabinet.catalog.repository.*;
import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.common.api.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogQueryService {
    private final CollectionRepository collections;
    private final FranchiseRepository franchises;
    private final CollectionItemRepository items;
    private final CollectionSourceItemRepository sourceItems;
    private final CollectionSectionRepository sections;
    private final FranchiseCollectionRepository franchiseCollections;
    private final FranchiseMediaRepository franchiseMedia;
    private final CollectionArtistRepository collectionArtists;

    public PageResponse<CollectionSummaryResponse> collections(CollectionType type, int page, int size) {
        Page<Collection> result = collections.findByStatusAndTypeOrderByTitleAscIdAsc(
                CatalogEntityStatus.ACTIVE, type, PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100))));
        return PageResponse.from(result.map(collection -> new CollectionSummaryResponse(
                collection.getId(), collection.getSlug(), collection.getTitle(), collection.getType().name(), null, null)));
    }

    @Cacheable(cacheNames = "collectionDetails", key = "#id")
    public CollectionResponse collection(UUID id, UUID viewerId) {
        Collection collection = collections.findById(id).orElseThrow(() -> notFound("COLLECTION_NOT_FOUND"));
        return collectionResponse(collection);
    }

    public CollectionResponse collectionBySlug(String slug, UUID viewerId) {
        Collection collection = collections.findBySlug(slug).orElseThrow(() -> notFound("COLLECTION_NOT_FOUND"));
        return collection(collection.getId(), viewerId);
    }

    private CollectionResponse collectionResponse(Collection collection) {
        List<CollectionItem> materialized = items.findByCollectionIdOrderByPositionAsc(collection.getId());
        List<CollectionResponse.SectionResponse> sectionResponses = sections
                .findByCollectionIdOrderByPositionAsc(collection.getId()).stream()
                .map(section -> new CollectionResponse.SectionResponse(section.getId(), section.getKey(),
                        section.getTitle(), section.getPosition())).toList();
        List<CollectionResponse.ItemResponse> itemResponses = materialized.stream()
                .map(item -> new CollectionResponse.ItemResponse(item.getId(), item.getMedia().getId(),
                        item.getMedia().getTitle(), item.getMedia().getTypeValue(), item.getMedia().getCoverUrl(),
                        item.getPosition(), item.getSection() == null ? null : item.getSection().getId(),
                        item.getRelationType().name())).toList();
        List<CollectionResponse.SourceItemResponse> sourceResponses = sourceItems
                .findByCollectionIdAndProviderAndSourcePresentTrueOrderByPositionAsc(collection.getId(), "TMDB")
                .stream().map(item -> new CollectionResponse.SourceItemResponse("TMDB", item.getExternalId(),
                        item.getResolvedMediaId(), item.getTitle(), item.getPosterUrl(), item.getReleaseDate(),
                        item.getPosition(), item.getResolutionStatus())).toList();
        List<FranchiseSummaryResponse> franchiseResponses = franchiseCollections.findByCollectionId(collection.getId())
                .stream().filter(item -> item.getFranchise().getStatus() == CatalogEntityStatus.ACTIVE)
                .map(item -> summary(item.getFranchise())).toList();
        return new CollectionResponse(collection.getId(), collection.getSlug(), collection.getTitle(),
                collection.getOriginalTitle(), collection.getDescription(), collection.getType().name(),
                collection.getSourceMode().name(), collection.getStatus().name(), collection.getPosterUrl(),
                collection.getBackdropUrl(), franchiseResponses, sectionResponses, itemResponses, null, sourceResponses);
    }

    @Cacheable(cacheNames = "franchiseDetails", key = "#id")
    public FranchiseResponse franchise(UUID id, UUID viewerId) {
        Franchise franchise = franchises.findById(id).orElseThrow(() -> notFound("FRANCHISE_NOT_FOUND"));
        return franchiseResponse(franchise);
    }

    public FranchiseResponse franchiseBySlug(String slug, UUID viewerId) {
        Franchise franchise = franchises.findBySlug(slug).orElseThrow(() -> notFound("FRANCHISE_NOT_FOUND"));
        return franchise(franchise.getId(), viewerId);
    }

    private FranchiseResponse franchiseResponse(Franchise franchise) {
        FranchiseSummaryResponse parent = franchise.getParent() == null ? null : summary(franchise.getParent());
        List<FranchiseSummaryResponse> children = franchises
                .findByParentIdAndStatusOrderByNameAsc(franchise.getId(), CatalogEntityStatus.ACTIVE)
                .stream().map(this::summary).toList();
        List<CollectionSummaryResponse> collectionResponses = franchiseCollections
                .findByFranchiseIdOrderByPositionAsc(franchise.getId()).stream()
                .filter(item -> item.getCollection().getStatus() == CatalogEntityStatus.ACTIVE)
                .map(item -> new CollectionSummaryResponse(item.getCollection().getId(), item.getCollection().getSlug(),
                        item.getCollection().getTitle(), item.getCollection().getType().name(), item.getPosition(),
                        items.findByCollectionIdOrderByPositionAsc(item.getCollection().getId()).size())).toList();
        List<FranchiseResponse.MediaSummaryResponse> mediaResponses = franchiseMedia.findByFranchiseId(franchise.getId())
                .stream().map(item -> new FranchiseResponse.MediaSummaryResponse(item.getMedia().getId(),
                        item.getMedia().getTitle(), item.getMedia().getTypeValue(), item.getMedia().getCoverUrl())).toList();
        return new FranchiseResponse(franchise.getId(), franchise.getSlug(), franchise.getName(),
                franchise.getOriginalName(), franchise.getDescription(), franchise.getType().name(),
                franchise.getStatus().name(), franchise.getPosterUrl(), franchise.getBackdropUrl(), parent,
                children, collectionResponses, mediaResponses, Map.of());
    }

    private FranchiseSummaryResponse summary(Franchise franchise) {
        return new FranchiseSummaryResponse(franchise.getId(), franchise.getSlug(), franchise.getName(),
                franchise.getType().name());
    }

    private ApiException notFound(String code) {
        return new ApiException(HttpStatus.NOT_FOUND, code, "Entidade de catálogo não encontrada");
    }
}
