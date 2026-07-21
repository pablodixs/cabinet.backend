package com.scriptles.cabinet.media.repository;

import com.scriptles.cabinet.media.entity.MediaLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface MediaLikeRepository extends JpaRepository<MediaLike, UUID> {
    @Modifying
    @Query(value = """
            insert into media_likes (id, user_id, media_id, created_at, liked_at)
            values (:id, :userId, :mediaId, current_timestamp, current_timestamp)
            on conflict (user_id, media_id) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("userId") UUID userId,
            @Param("mediaId") UUID mediaId
    );

    @Modifying
    @Query(value = """
            insert into media_likes (id, user_id, media_id, created_at, liked_at)
            values (:id, :userId, :mediaId, current_timestamp, :likedAt)
            on conflict (user_id, media_id) do nothing
            """, nativeQuery = true)
    int insertIfAbsentAt(
            @Param("id") UUID id,
            @Param("userId") UUID userId,
            @Param("mediaId") UUID mediaId,
            @Param("likedAt") Instant likedAt
    );

    boolean existsByUserIdAndMediaId(UUID userId, UUID mediaId);

    long countByMediaId(UUID mediaId);

    @EntityGraph(attributePaths = "user")
    List<MediaLike> findTop3ByMediaIdOrderByLikedAtDescIdDesc(UUID mediaId);

    @EntityGraph(attributePaths = "user")
    @Query("""
            select mediaLike from MediaLike mediaLike
            where mediaLike.media.id = :mediaId
              and not exists (select block.id from UserBlock block
                  where (block.blocker.id = :viewerId and block.blocked.id = mediaLike.user.id)
                     or (block.blocker.id = mediaLike.user.id and block.blocked.id = :viewerId))
            order by mediaLike.likedAt desc, mediaLike.id desc
            """)
    List<MediaLike> findRecentVisibleLikers(
            @Param("mediaId") UUID mediaId,
            @Param("viewerId") UUID viewerId,
            Pageable pageable);

    @Query("""
            select mediaLike.media.id as mediaId, count(mediaLike.id) as activityCount
            from MediaLike mediaLike
            where mediaLike.media.typeValue in :types
              and coalesce(mediaLike.likedAt, mediaLike.createdAt) >= :since
            group by mediaLike.media.id
            order by count(mediaLike.id) desc, mediaLike.media.id asc
            """)
    List<MediaActivityProjection> findRecentActivity(@Param("types") Set<String> types,
                                                      @Param("since") Instant since,
                                                      Pageable pageable);

    long deleteByUserIdAndMediaId(UUID userId, UUID mediaId);

    interface MediaActivityProjection {
        UUID getMediaId();
        long getActivityCount();
    }
}
