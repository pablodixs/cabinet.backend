package com.scriptles.cabinet.media.repository;

import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface MediaRepository extends JpaRepository<Media, UUID> {
    Page<Media> findByTitleContainingIgnoreCase(
            String title,
            Pageable pageable
    );

    Page<Media> findByTitleContainingIgnoreCaseOrOriginalTitleContainingIgnoreCase(
            String title,
            String originalTitle,
            Pageable pageable
    );

    Page<Media> findByTypeValue(
            String type,
            Pageable pageable
    );

    Optional<Media> findFirstByWikidataId(String wikidataId);

    @Query("select b.media from BookDetails b where b.canonicalWorkWikidataId = :wikidataId")
    Optional<Media> findFirstByCanonicalBookWorkWikidataId(@Param("wikidataId") String wikidataId);
}
