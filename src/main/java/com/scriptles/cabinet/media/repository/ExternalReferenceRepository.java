package com.scriptles.cabinet.media.repository;

import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.enums.ExternalSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExternalReferenceRepository
        extends JpaRepository<ExternalReference, UUID> {

    Optional<ExternalReference> findBySourceAndExternalId(
            ExternalSource source,
            String externalId
    );

    List<ExternalReference> findAllByMediaId(UUID mediaId);

    Optional<ExternalReference> findByMediaIdAndSource(
            UUID mediaId,
            ExternalSource source
    );

    boolean existsBySourceAndExternalId(
            ExternalSource source,
            String externalId
    );
}