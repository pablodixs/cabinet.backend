package com.scriptles.cabinet.media.repository;

import com.scriptles.cabinet.media.entity.AwardSyncState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AwardSyncStateRepository extends JpaRepository<AwardSyncState, UUID> {
    Optional<AwardSyncState> findByMediaId(UUID mediaId);

    Optional<AwardSyncState> findByPersonId(UUID personId);
}
