package com.scriptles.cabinet.media.repository;

import com.scriptles.cabinet.media.entity.MediaTranslation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface MediaTranslationRepository extends JpaRepository<MediaTranslation, UUID> {
    Optional<MediaTranslation> findByMediaIdAndLocale(UUID mediaId, String locale);
    List<MediaTranslation> findAllByMediaId(UUID mediaId);

    @Query("""
            select translation
            from MediaTranslation translation
            where translation.media.id in :mediaIds
            """)
    List<MediaTranslation> findForResolution(@Param("mediaIds") Collection<UUID> mediaIds);

    List<MediaTranslation> findAllByMediaIdInAndLocaleIn(
            Collection<UUID> mediaIds,
            Collection<String> locales
    );
}
