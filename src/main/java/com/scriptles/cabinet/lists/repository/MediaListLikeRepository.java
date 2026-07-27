package com.scriptles.cabinet.lists.repository;

import com.scriptles.cabinet.lists.entity.MediaListLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MediaListLikeRepository extends JpaRepository<MediaListLike, UUID> {
    @Modifying
    @Query(value = """
            insert into media_list_likes (id, user_id, list_id, created_at)
            values (:id, :userId, :listId, current_timestamp)
            on conflict (user_id, list_id) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("userId") UUID userId,
            @Param("listId") UUID listId
    );

    boolean existsByUserIdAndListId(UUID userId, UUID listId);

    long countByListId(UUID listId);

    long countByListIdAndUserIdNot(UUID listId, UUID userId);

    Optional<MediaListLike> findFirstByListIdAndUserIdNotOrderByCreatedAtDescIdDesc(
            UUID listId, UUID userId);

    long deleteByUserIdAndListId(UUID userId, UUID listId);

    long deleteByListId(UUID listId);

    @Query("""
            select listLike.list.id as listId, count(listLike) as likeCount
            from MediaListLike listLike
            where listLike.list.id in :listIds
            group by listLike.list.id
            """)
    List<MediaListLikeCount> countByListIds(@Param("listIds") Collection<UUID> listIds);

    interface MediaListLikeCount {
        UUID getListId();

        long getLikeCount();
    }
}
