package com.scriptles.cabinet.media.repository;

import com.scriptles.cabinet.media.entity.MediaTranslation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface MediaTranslationRepository extends JpaRepository<MediaTranslation, UUID> {
    Optional<MediaTranslation> findByMediaIdAndLocale(UUID mediaId, String locale);
    List<MediaTranslation> findAllByMediaId(UUID mediaId);
}
