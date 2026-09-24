package com.scriptles.cabinet.catalog.repository;

import com.scriptles.cabinet.catalog.entity.CatalogJob;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CatalogJobRepository extends JpaRepository<CatalogJob, UUID> {
    @Query(value = """
            select * from catalog_jobs
            where status in ('PENDING', 'RETRY') and available_at <= :now
            order by priority desc, available_at asc, created_at asc
            for update skip locked
            """, nativeQuery = true)
    List<CatalogJob> claimable(@Param("now") Instant now, Pageable pageable);

    @Query(value = """
            select * from catalog_jobs
            where status = 'PROCESSING' and locked_at < :cutoff
            order by locked_at asc
            for update skip locked
            """, nativeQuery = true)
    List<CatalogJob> staleProcessing(@Param("cutoff") Instant cutoff, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update catalog_jobs
            set status = 'RETRY', available_at = :availableAt,
                locked_at = null, locked_by = null,
                last_error = 'Processing lock expired', updated_at = now()
            where status = 'PROCESSING' and locked_at < :cutoff
            """, nativeQuery = true)
    int releaseStaleProcessing(@Param("cutoff") Instant cutoff, @Param("availableAt") Instant availableAt);

    long countByStatus(String status);

    long countByStatusAndCompletedAtAfter(String status, Instant completedAfter);

    long countByOperationIdAndStatusIn(UUID operationId, List<String> statuses);

    List<CatalogJob> findByOperationIdOrderByCreatedAtAsc(UUID operationId);

    boolean existsByDeduplicationKeyAndStatusIn(String deduplicationKey, List<String> statuses);

    java.util.Optional<CatalogJob> findFirstByDeduplicationKeyAndStatusInOrderByCreatedAtAsc(
            String deduplicationKey, List<String> statuses);

    List<CatalogJob> findByStatusOrderByCreatedAtAsc(String status, Pageable pageable);

    @Query(value = """
            select * from catalog_jobs
            where (:status is null or status = :status)
              and (:jobType is null or job_type = :jobType)
              and (:provider is null or provider = :provider)
              and (:query is null or lower(coalesce(external_id, '')) like lower(concat('%', :query, '%'))
                   or lower(cast(id as varchar)) like lower(concat('%', :query, '%'))
                   or (:operationId is not null and operation_id = :operationId))
            order by created_at desc
            """,
            countQuery = """
            select count(*) from catalog_jobs
            where (:status is null or status = :status)
              and (:jobType is null or job_type = :jobType)
              and (:provider is null or provider = :provider)
              and (:query is null or lower(coalesce(external_id, '')) like lower(concat('%', :query, '%'))
                   or lower(cast(id as varchar)) like lower(concat('%', :query, '%'))
                   or (:operationId is not null and operation_id = :operationId))
            """, nativeQuery = true)
    org.springframework.data.domain.Page<CatalogJob> search(
            @Param("status") String status,
            @Param("jobType") String jobType,
            @Param("provider") String provider,
            @Param("query") String query,
            @Param("operationId") UUID operationId,
            Pageable pageable
    );

    @Query("select min(job.createdAt) from CatalogJob job where job.status = :status")
    Instant findOldestCreatedAtByStatus(@Param("status") String status);
}
