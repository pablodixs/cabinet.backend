package com.scriptles.cabinet.media.repository;

import com.scriptles.cabinet.media.entity.MediaLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface MediaLikeRepository extends JpaRepository<MediaLike, UUID> {
    @Modifying
    @Query(value = """
            insert into media_likes (id, user_id, media_id, created_at)
            values (:id, :userId, :mediaId, current_timestamp)
            on conflict (user_id, media_id) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("userId") UUID userId,
            @Param("mediaId") UUID mediaId
    );

    boolean existsByUserIdAndMediaId(UUID userId, UUID mediaId);

    long countByMediaId(UUID mediaId);

    long deleteByUserIdAndMediaId(UUID userId, UUID mediaId);
}
