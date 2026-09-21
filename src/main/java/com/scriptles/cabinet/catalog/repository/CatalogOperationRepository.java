package com.scriptles.cabinet.catalog.repository;

import com.scriptles.cabinet.catalog.entity.CatalogOperation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface CatalogOperationRepository extends JpaRepository<CatalogOperation, UUID> {
    @Query(value = """
            select * from catalog_operations
            where (:status is null or status = :status)
              and (:type is null or type = :type)
              and (:provider is null or provider = :provider)
              and (:trigger is null or trigger = :trigger)
              and (:query is null or lower(coalesce(root_external_id, '')) like lower(concat('%', :query, '%'))
                   or lower(cast(id as varchar)) like lower(concat('%', :query, '%'))
                   or (:rootId is not null and root_collection_id = :rootId))
            order by created_at desc
            """,
            countQuery = """
            select count(*) from catalog_operations
            where (:status is null or status = :status)
              and (:type is null or type = :type)
              and (:provider is null or provider = :provider)
              and (:trigger is null or trigger = :trigger)
              and (:query is null or lower(coalesce(root_external_id, '')) like lower(concat('%', :query, '%'))
                   or lower(cast(id as varchar)) like lower(concat('%', :query, '%'))
                   or (:rootId is not null and root_collection_id = :rootId))
            """, nativeQuery = true)
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
