package com.scriptles.cabinet.media.repository;

import com.scriptles.cabinet.media.entity.MediaExternalRating;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MediaExternalRatingRepository extends JpaRepository<MediaExternalRating, UUID> {
    List<MediaExternalRating> findAllByMediaIdOrderByMetricAsc(UUID mediaId);

    void deleteAllByMediaIdAndProvider(UUID mediaId, com.scriptles.cabinet.media.enums.ExternalSource provider);
}
