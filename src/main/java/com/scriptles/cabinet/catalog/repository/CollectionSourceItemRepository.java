package com.scriptles.cabinet.catalog.repository;

import com.scriptles.cabinet.catalog.entity.CollectionSourceItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CollectionSourceItemRepository extends JpaRepository<CollectionSourceItem, UUID> {
    List<CollectionSourceItem> findByCollectionIdAndProvider(UUID collectionId, String provider);

    List<CollectionSourceItem> findByCollectionIdAndProviderAndSourcePresentTrueOrderByPositionAsc(
            UUID collectionId, String provider);

    Optional<CollectionSourceItem> findByCollectionIdAndProviderAndExternalId(
            UUID collectionId, String provider, String externalId);

    List<CollectionSourceItem> findByProviderAndExternalIdAndSourcePresentTrue(String provider, String externalId);
}
