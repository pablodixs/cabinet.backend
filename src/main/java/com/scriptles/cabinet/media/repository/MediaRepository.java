package com.scriptles.cabinet.media.repository;

import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MediaRepository extends JpaRepository<Media, UUID> {
    Optional<Media> findByExternalSourceAndExternalId(
            ExternalSource externalSource,
            String externalId
    );

    Page<Media> findByTitleContainingIgnoreCase(
            String title,
            Pageable pageable
    );

    Page<Media> findByType(
            MediaType type,
            Pageable pageable
    );
}
