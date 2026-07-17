package com.scriptles.cabinet.media.repository;

import com.scriptles.cabinet.media.entity.ExternalInfoSnapshot;
import com.scriptles.cabinet.media.enums.ExternalInfoKind;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ExternalInfoSnapshotRepository extends JpaRepository<ExternalInfoSnapshot, UUID> {
    Optional<ExternalInfoSnapshot> findByMediaIdAndKindAndRegion(
            UUID mediaId,
            ExternalInfoKind kind,
            String region
    );
}
