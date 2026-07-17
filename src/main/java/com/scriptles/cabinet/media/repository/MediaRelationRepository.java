package com.scriptles.cabinet.media.repository;

import com.scriptles.cabinet.media.entity.MediaRelation;
import com.scriptles.cabinet.media.enums.MediaRelationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MediaRelationRepository extends JpaRepository<MediaRelation, UUID> {
    List<MediaRelation> findAllBySourceMediaIdOrderByCreatedAtAsc(UUID sourceMediaId);

    boolean existsBySourceMediaIdAndTargetMediaIdAndRelationType(
            UUID sourceMediaId,
            UUID targetMediaId,
            MediaRelationType relationType
    );
}
