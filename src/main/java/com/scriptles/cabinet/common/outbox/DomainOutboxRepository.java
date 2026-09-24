package com.scriptles.cabinet.common.outbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface DomainOutboxRepository extends JpaRepository<DomainOutboxEvent, UUID> {
    @Query("select e.status as status, count(e) as count from DomainOutboxEvent e group by e.status")
    List<OutboxStatusCount> countByStatus();

    @Query(value = """
            select *
            from domain_outbox_events
            where status in ('PENDING', 'RETRY')
              and available_at <= :now
            order by created_at, id
            for update skip locked
            """, nativeQuery = true)
    List<DomainOutboxEvent> claimable(Instant now, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from DomainOutboxEvent e where e.id = :id")
    java.util.Optional<DomainOutboxEvent> findByIdForUpdate(UUID id);

    interface OutboxStatusCount {
        DomainOutboxStatus getStatus();
        long getCount();
    }

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update domain_outbox_events
            set status = case when attempt_count + 1 >= :maxAttempts then 'DEAD' else 'RETRY' end,
                attempt_count = attempt_count + 1,
                available_at = :availableAt,
                last_error = 'Processing lock expired',
                processing_started_at = null,
                locked_by = null
            where status = 'PROCESSING'
              and processing_started_at < :cutoff
            """, nativeQuery = true)
    int releaseStaleProcessing(Instant cutoff, Instant availableAt, int maxAttempts);
}
