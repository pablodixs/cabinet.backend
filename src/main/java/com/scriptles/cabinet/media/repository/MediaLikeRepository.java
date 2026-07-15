package com.scriptles.cabinet.media.repository;

import com.scriptles.cabinet.media.entity.MediaLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MediaLikeRepository extends JpaRepository<MediaLike, UUID> {
    boolean existsByUserIdAndMediaId(UUID userId, UUID mediaId);

    long countByMediaId(UUID mediaId);

    long deleteByUserIdAndMediaId(UUID userId, UUID mediaId);
}
