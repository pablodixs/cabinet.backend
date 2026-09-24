package com.scriptles.cabinet.media.repository;

import com.scriptles.cabinet.media.entity.PersonExternalReference;
import com.scriptles.cabinet.media.enums.ExternalSource;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PersonExternalReferenceRepository
        extends JpaRepository<PersonExternalReference, UUID> {

    @EntityGraph(attributePaths = "person")
    Optional<PersonExternalReference> findBySourceAndExternalId(
            ExternalSource source,
            String externalId
    );

    @EntityGraph(attributePaths = "person")
    List<PersonExternalReference> findAllBySourceAndExternalIdIn(
            ExternalSource source,
            List<String> externalIds
    );

    List<PersonExternalReference> findAllByPersonId(UUID personId);

    Optional<PersonExternalReference> findFirstByPersonIdAndSource(
            UUID personId,
            ExternalSource source
    );
}
