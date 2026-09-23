package com.scriptles.cabinet.catalog.repository;

import com.scriptles.cabinet.catalog.entity.CollectionTranslation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CollectionTranslationRepository extends JpaRepository<CollectionTranslation, UUID> {
    Optional<CollectionTranslation> findByCollectionIdAndLocale(UUID collectionId, String locale);
    List<CollectionTranslation> findAllByCollectionIdIn(Collection<UUID> collectionIds);
}
