package com.scriptles.cabinet.lists.repository;

import com.scriptles.cabinet.lists.entity.MediaListLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MediaListLikeRepository extends JpaRepository<MediaListLike, UUID> {
    boolean existsByUserIdAndListId(UUID userId, UUID listId);

    long countByListId(UUID listId);

    long countByListIdAndUserIdNot(UUID listId, UUID userId);

    Optional<MediaListLike> findFirstByListIdAndUserIdNotOrderByCreatedAtDescIdDesc(
            UUID listId, UUID userId);

    long deleteByUserIdAndListId(UUID userId, UUID listId);

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
