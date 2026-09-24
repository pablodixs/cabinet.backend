package com.scriptles.cabinet.user.importer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

public interface LetterboxdImportJobRepository extends JpaRepository<LetterboxdImportJob, UUID> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update LetterboxdImportJob job
            set job.state = :targetState,
                job.completedAt = :completedAt,
                job.expiresAt = :expiresAt,
                job.errorMessage = :errorMessage
            where job.id = :jobId and job.state = :expectedState
            """)
    int transitionState(@Param("jobId") UUID jobId,
                        @Param("expectedState") LetterboxdImportJobState expectedState,
                        @Param("targetState") LetterboxdImportJobState targetState,
                        @Param("completedAt") Instant completedAt,
                        @Param("expiresAt") Instant expiresAt,
                        @Param("errorMessage") String errorMessage);

    Optional<LetterboxdImportJob> findByIdAndUserId(UUID id, UUID userId);
    Optional<LetterboxdImportJob> findFirstByUserIdAndStateInOrderByCreatedAtDesc(
            UUID userId,
            Collection<LetterboxdImportJobState> states
    );
    boolean existsByUserIdAndStateIn(UUID userId, Collection<LetterboxdImportJobState> states);
    List<LetterboxdImportJob> findAllByStateIn(Collection<LetterboxdImportJobState> states);
    List<LetterboxdImportJob> findAllByExpiresAtBeforeAndStateIn(
            Instant expiresAt,
            Collection<LetterboxdImportJobState> states
    );

    long countByStateIn(Collection<LetterboxdImportJobState> states);

    long countByStateInAndCompletedAtAfter(Collection<LetterboxdImportJobState> states, Instant completedAt);

    List<LetterboxdImportJob> findTop10ByStateInAndCompletedAtAfterOrderByCompletedAtDesc(
            Collection<LetterboxdImportJobState> states,
            Instant completedAt
    );
}
