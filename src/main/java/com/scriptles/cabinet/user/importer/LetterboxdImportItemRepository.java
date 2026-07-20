package com.scriptles.cabinet.user.importer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LetterboxdImportItemRepository extends JpaRepository<LetterboxdImportItem, UUID> {
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
