package com.scriptles.cabinet.media.repository;

import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.enums.ExternalSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface ExternalReferenceRepository
        extends JpaRepository<ExternalReference, UUID> {

    Optional<ExternalReference> findBySourceAndExternalId(
            ExternalSource source,
            String externalId
    );

    List<ExternalReference> findAllByMediaId(UUID mediaId);

    List<ExternalReference> findAllByMediaIdIn(Collection<UUID> mediaIds);

    List<ExternalReference> findAllByMediaIdInAndPrimaryReferenceTrue(
            Collection<UUID> mediaIds
    );

    List<ExternalReference> findAllBySourceInAndExternalIdIn(
            Set<ExternalSource> sources,
            Set<String> externalIds
    );

    List<ExternalReference> findAllBySourceAndExternalIdIn(
            ExternalSource source,
            Collection<String> externalIds
    );

    Optional<ExternalReference> findByMediaIdAndSource(
            UUID mediaId,
            ExternalSource source
    );

    boolean existsBySourceAndExternalId(
            ExternalSource source,
            String externalId
    );

    @Query("""
            select reference.externalId
            from ExternalReference reference
            where reference.source = :source
              and reference.media.typeValue = :mediaType
              and reference.externalId is not null
            order by reference.externalId
            """)
    List<String> findExternalIdsBySourceAndMediaType(
            @Param("source") ExternalSource source,
            @Param("mediaType") String mediaType
    );
}
