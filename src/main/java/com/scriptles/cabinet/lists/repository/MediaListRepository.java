package com.scriptles.cabinet.lists.repository;

import com.scriptles.cabinet.lists.entity.MediaList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface MediaListRepository extends JpaRepository<MediaList, UUID> {
    List<MediaList> findAllByOwnerIdOrderByUpdatedAtDesc(UUID ownerId);

    Optional<MediaList> findByIdAndOwnerId(UUID id, UUID ownerId);

    @Query("""
            select list
            from MediaList list
            join fetch list.owner
            where list.id = :listId
            """)
    Optional<MediaList> findWithOwnerById(@Param("listId") UUID listId);

    @Query("""
            select list
            from MediaList list
            join fetch list.owner
            where list.id in :listIds
            """)
    List<MediaList> findAllWithOwnerByIdIn(@Param("listIds") Collection<UUID> listIds);
}
