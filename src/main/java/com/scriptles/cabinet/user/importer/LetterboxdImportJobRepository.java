package com.scriptles.cabinet.user.importer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

public interface LetterboxdImportJobRepository extends JpaRepository<LetterboxdImportJob, UUID> {
    Optional<LetterboxdImportJob> findByIdAndUserId(UUID id, UUID userId);
    boolean existsByUserIdAndStateIn(UUID userId, Collection<LetterboxdImportJobState> states);
    List<LetterboxdImportJob> findAllByStateIn(Collection<LetterboxdImportJobState> states);
    List<LetterboxdImportJob> findAllByExpiresAtBeforeAndStateIn(
            Instant expiresAt,
            Collection<LetterboxdImportJobState> states
    );
}
