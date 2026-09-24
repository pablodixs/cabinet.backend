package com.scriptles.cabinet.user.importer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LetterboxdImportItemRepository extends JpaRepository<LetterboxdImportItem, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from LetterboxdImportItem item where item.id = :id")
    Optional<LetterboxdImportItem> findByIdForUpdate(@Param("id") UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update letterboxd_import_items
            set state = 'FAILED', error_message = :errorMessage
            where id = :itemId and state in ('AUTO_MATCHED', 'RESOLVED', 'FAILED')
            """, nativeQuery = true)
    int markApplyFailedIfRetryable(@Param("itemId") UUID itemId,
                                   @Param("errorMessage") String errorMessage);

    @EntityGraph(attributePaths = {"job.user", "selectedMedia"})
    List<LetterboxdImportItem> findAllByJobIdOrderByCreatedAtAsc(UUID jobId);
    Page<LetterboxdImportItem> findAllByJobId(UUID jobId, Pageable pageable);
    Page<LetterboxdImportItem> findAllByJobIdAndState(UUID jobId, LetterboxdImportItemState state, Pageable pageable);
    Optional<LetterboxdImportItem> findByIdAndJobId(UUID id, UUID jobId);
    long countByJobIdAndState(UUID jobId, LetterboxdImportItemState state);

    @Modifying
    @Query("""
            update LetterboxdImportItem item
            set item.payload = '{}', item.matchCandidates = null
            where item.job.id = :jobId
            """)
    int redactPayloads(@Param("jobId") UUID jobId);
}
