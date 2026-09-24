package com.scriptles.cabinet.catalog.service;

import com.scriptles.cabinet.catalog.api.*;
import com.scriptles.cabinet.catalog.collection.CollectionType;
import com.scriptles.cabinet.catalog.domain.CatalogEntityStatus;
import com.scriptles.cabinet.catalog.entity.*;
import com.scriptles.cabinet.catalog.repository.*;
import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.user.enums.UserMediaStatus;
import com.scriptles.cabinet.user.repository.UserMediaRepository;
import com.scriptles.cabinet.media.translation.MediaTranslationResolver;
import com.scriptles.cabinet.media.translation.ResolvedMediaTranslation;
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
    private final UserMediaRepository userMedia;
    private final CollectionTranslationResolver collectionTranslations;
    private final MediaTranslationResolver mediaTranslations;

    public PageResponse<CollectionSummaryResponse> collections(CollectionType type, int page, int size, String sort, String locale) {
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)));
        Page<Collection> result = "TITLE".equalsIgnoreCase(sort)
                ? collections.findByStatusAndTypeOrderByTitleAscIdAsc(CatalogEntityStatus.ACTIVE, type, pageable)
                : collections.findByStatusAndTypeOrderByPopularityDesc(
                        CatalogEntityStatus.ACTIVE.name(), type.name(), pageable);
        Map<UUID, CollectionTranslationResolver.ResolvedCollection> localized =
                collectionTranslations.resolveAll(result.getContent(), locale);
        return PageResponse.from(result.map(collection -> new CollectionSummaryResponse(
                collection.getId(), collection.getSlug(), localized.get(collection.getId()).title(),
                collection.getType().name(), null, null)));
    }

    public CollectionResponse collection(UUID id, UUID viewerId, String locale) {
        Collection collection = collections.findById(id).orElseThrow(() -> notFound("COLLECTION_NOT_FOUND"));
        return collectionResponse(collection, viewerId, locale);
    }

    @Cacheable(cacheNames = "collectionDetails",
            key = "#id + ':' + #locale + ':' + #publicVersion",
            unless = "#viewerId != null")
    public CollectionResponse collection(UUID id, UUID viewerId, String locale, String publicVersion) {
        Collection collection = collections.findById(id).orElseThrow(() -> notFound("COLLECTION_NOT_FOUND"));
        return collectionResponse(collection, viewerId, locale);
    }

    public CollectionResponse collectionBySlug(String slug, UUID viewerId, String locale) {
        Collection collection = collections.findBySlug(slug).orElseThrow(() -> notFound("COLLECTION_NOT_FOUND"));
        return collectionResponse(collection, viewerId, locale);
    }

    private CollectionResponse collectionResponse(Collection collection, UUID viewerId, String locale) {
        List<CollectionItem> materialized = items.findByCollectionIdOrderByPositionAsc(collection.getId());
        Map<UUID, ResolvedMediaTranslation> localizedMedia = mediaTranslations.resolveAll(
                materialized.stream().map(CollectionItem::getMedia).toList(), locale);
        CollectionTranslationResolver.ResolvedCollection localized = collectionTranslations.resolve(collection, locale);
        List<CollectionResponse.SectionResponse> sectionResponses = sections
                .findByCollectionIdOrderByPositionAsc(collection.getId()).stream()
                .map(section -> new CollectionResponse.SectionResponse(section.getId(), section.getKey(),
                        section.getTitle(), section.getPosition())).toList();
        List<CollectionResponse.ItemResponse> itemResponses = materialized.stream()
                .map(item -> new CollectionResponse.ItemResponse(item.getId(), item.getMedia().getId(),
                        localizedMedia.get(item.getMedia().getId()).title(), item.getMedia().getTypeValue(),
                        localizedMedia.get(item.getMedia().getId()).coverUrl(),
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
        CollectionResponse.ViewerResponse viewer = viewerId == null
                ? null
                : viewerProgress(viewerId, materialized);
        return new CollectionResponse(collection.getId(), collection.getSlug(), localized.title(),
                collection.getOriginalTitle(), localized.description(), collection.getType().name(),
                collection.getSourceMode().name(), collection.getStatus().name(), localized.posterUrl(),
                localized.backdropUrl(), franchiseResponses, sectionResponses, itemResponses, viewer, sourceResponses,
                localized.requestedLocale(), localized.resolvedLocale(), localized.fallback());
    }

    private CollectionResponse.ViewerResponse viewerProgress(UUID viewerId, List<CollectionItem> items) {
        List<UUID> mediaIds = items.stream()
                .map(item -> item.getMedia().getId())
                .toList();
        if (mediaIds.isEmpty()) {
            return new CollectionResponse.ViewerResponse(0, 0, 0d, 0, null);
        }

        long completedCount = userMedia.findAllByUserIdAndMediaIdIn(viewerId, mediaIds).stream()
                .filter(entry -> entry.getStatus() == UserMediaStatus.COMPLETED)
                .count();
        double completion = completedCount * 100d / mediaIds.size();
        return new CollectionResponse.ViewerResponse(
                (int) completedCount,
                mediaIds.size(),
                completion,
                0,
                null);
    }

    public FranchiseResponse franchise(UUID id, UUID viewerId) {
        Franchise franchise = franchises.findById(id).orElseThrow(() -> notFound("FRANCHISE_NOT_FOUND"));
        return franchiseResponse(franchise);
    }

    @Cacheable(cacheNames = "franchiseDetails",
            key = "#id + ':' + #publicVersion",
            unless = "#viewerId != null")
    public FranchiseResponse franchise(UUID id, UUID viewerId, String publicVersion) {
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
