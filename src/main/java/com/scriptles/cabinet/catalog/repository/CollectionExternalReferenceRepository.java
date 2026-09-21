package com.scriptles.cabinet.catalog.repository;

import com.scriptles.cabinet.catalog.collection.CollectionSourceMode;
import com.scriptles.cabinet.catalog.collection.CollectionType;
import com.scriptles.cabinet.catalog.entity.CollectionExternalReference;
import com.scriptles.cabinet.media.enums.ExternalSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CollectionExternalReferenceRepository extends JpaRepository<CollectionExternalReference, UUID> {
    Optional<CollectionExternalReference> findByProviderAndExternalId(ExternalSource provider, String externalId);

    List<CollectionExternalReference> findByCollectionId(UUID collectionId);

    @Query("""
            select reference.collection.id
            from CollectionExternalReference reference
            where reference.provider = :provider
              and reference.collection.type = :type
              and reference.collection.sourceMode = :sourceMode
              and (reference.lastSyncedAt is null or reference.lastSyncedAt <= :cutoff)
            order by case when reference.lastSyncedAt is null then 0 else 1 end,
                     reference.lastSyncedAt, reference.collection.id
            """)
    List<UUID> findCollectionIdsDueForSync(
            @Param("provider") ExternalSource provider,
            @Param("type") CollectionType type,
            @Param("sourceMode") CollectionSourceMode sourceMode,
            @Param("cutoff") Instant cutoff
    );
}
