package com.scriptles.cabinet.media.repository;

import com.scriptles.cabinet.media.entity.MediaRelation;
import com.scriptles.cabinet.media.enums.MediaRelationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MediaRelationRepository extends JpaRepository<MediaRelation, UUID> {
    List<MediaRelation> findAllBySourceMediaIdOrderByCreatedAtAsc(UUID sourceMediaId);

    boolean existsBySourceMediaIdAndTargetMediaIdAndRelationType(
            UUID sourceMediaId,
            UUID targetMediaId,
            MediaRelationType relationType
    );

    @EntityGraph(attributePaths = {"sourceMedia", "targetMedia"})
    @Query("select relation from MediaRelation relation "
            + "where relation.sourceMedia.id in :mediaIds or relation.targetMedia.id in :mediaIds")
    List<MediaRelation> findAllConnectedTo(@Param("mediaIds") Collection<UUID> mediaIds);
}
