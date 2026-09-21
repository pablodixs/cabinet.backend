package com.scriptles.cabinet.catalog.repository;

import com.scriptles.cabinet.catalog.entity.CatalogJobAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CatalogJobAttemptRepository extends JpaRepository<CatalogJobAttempt, UUID> {
    List<CatalogJobAttempt> findByJobIdOrderByAttemptNumberDesc(UUID jobId);

    java.util.Optional<CatalogJobAttempt> findByJobIdAndAttemptNumber(UUID jobId, int attemptNumber);
}
