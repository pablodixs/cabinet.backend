package com.scriptles.cabinet.media.repository;

import com.scriptles.cabinet.media.entity.CatalogSyncCheckpoint;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CatalogSyncCheckpointRepository extends JpaRepository<CatalogSyncCheckpoint, java.util.UUID> {
    Optional<CatalogSyncCheckpoint> findBySourceAndMediaType(ExternalSource source, MediaType mediaType);
}
