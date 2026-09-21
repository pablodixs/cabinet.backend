package com.scriptles.cabinet.catalog.repository;

import com.scriptles.cabinet.catalog.entity.ExternalCatalogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ExternalCatalogEntityRepository extends JpaRepository<ExternalCatalogEntity, UUID> {
    @Query("""
            select entity from ExternalCatalogEntity entity
            where (:provider is null or entity.provider = :provider)
              and (:entityType is null or entity.entityType = :entityType)
              and (:state is null or entity.state = :state)
              and (:query is null or lower(coalesce(entity.displayName, '')) like lower(concat('%', :query, '%'))
                   or lower(entity.externalId) like lower(concat('%', :query, '%')))
            order by entity.lastSeenAt desc
            """)
    Page<ExternalCatalogEntity> search(@Param("provider") String provider,
            @Param("entityType") String entityType, @Param("state") String state,
            @Param("query") String query, Pageable pageable);
}
