package com.scriptles.cabinet.media.repository;

import com.scriptles.cabinet.media.entity.CatalogOutboxEvent;
import com.scriptles.cabinet.media.enums.CatalogEventType;
import com.scriptles.cabinet.media.enums.CatalogOutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CatalogOutboxRepository extends JpaRepository<CatalogOutboxEvent, UUID> {
    @Query("select e.status as status, count(e) as count from CatalogOutboxEvent e group by e.status")
    List<OutboxStatusCount> countByStatus();

    boolean existsByAggregateIdAndEventTypeAndStatusIn(
            UUID aggregateId,
            CatalogEventType eventType,
            Collection<CatalogOutboxStatus> statuses
    );

    @Query(value = """
            select *
            from catalog_outbox
            where status in ('PENDING', 'RETRY')
              and available_at <= :now
            order by created_at
            for update skip locked
            """, nativeQuery = true)
    List<CatalogOutboxEvent> claimable(Instant now, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update catalog_outbox
            set status = 'RETRY',
                available_at = :availableAt,
                locked_at = null,
                locked_by = null
            where status = 'PROCESSING'
              and locked_at < :cutoff
            """, nativeQuery = true)
    int releaseStaleProcessing(Instant cutoff, Instant availableAt);

    interface OutboxStatusCount {
        CatalogOutboxStatus getStatus();
        long getCount();
    }
}
