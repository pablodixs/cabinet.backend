package com.scriptles.cabinet.catalog.repository;

import com.scriptles.cabinet.catalog.entity.CatalogOperation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface CatalogOperationRepository extends JpaRepository<CatalogOperation, UUID> {
    @Query("""
            select operation from CatalogOperation operation
            where (:status is null or operation.status = :status)
              and (:type is null or operation.type = :type)
              and (:provider is null or operation.provider = :provider)
              and (:trigger is null or operation.trigger = :trigger)
              and (:query is null or lower(coalesce(operation.rootExternalId, '')) like lower(concat('%', :query, '%'))
                   or lower(cast(operation.id as string)) like lower(concat('%', :query, '%'))
                   or (:rootId is not null and operation.rootCollectionId = :rootId))
            order by operation.createdAt desc
            """)
    Page<CatalogOperation> search(
            @Param("status") String status,
            @Param("type") String type,
            @Param("provider") String provider,
            @Param("trigger") String trigger,
            @Param("query") String query,
            @Param("rootId") java.util.UUID rootId,
            Pageable pageable
    );
}
