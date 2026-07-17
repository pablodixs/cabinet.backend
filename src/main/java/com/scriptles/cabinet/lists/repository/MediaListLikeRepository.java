package com.scriptles.cabinet.lists.repository;

import com.scriptles.cabinet.lists.entity.MediaListLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MediaListLikeRepository extends JpaRepository<MediaListLike, UUID> {
    boolean existsByUserIdAndListId(UUID userId, UUID listId);

    long countByListId(UUID listId);

    long countByListIdAndUserIdNot(UUID listId, UUID userId);

    Optional<MediaListLike> findFirstByListIdAndUserIdNotOrderByCreatedAtDescIdDesc(
            UUID listId, UUID userId);

    long deleteByUserIdAndListId(UUID userId, UUID listId);
}
