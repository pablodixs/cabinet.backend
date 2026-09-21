package com.scriptles.cabinet.catalog.repository;

import com.scriptles.cabinet.catalog.entity.CollectionSourceSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CollectionSourceSnapshotRepository extends JpaRepository<CollectionSourceSnapshot, UUID> {
    Optional<CollectionSourceSnapshot> findByCollectionIdAndProvider(UUID collectionId, String provider);
}
