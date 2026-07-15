package com.scriptles.cabinet.lists.repository;

import com.scriptles.cabinet.lists.entity.MediaListItem;
import com.scriptles.cabinet.user.enums.Visibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MediaListItemRepository extends JpaRepository<MediaListItem, UUID> {
    @Query("""
            select item.list.id as listId, count(item) as itemCount
            from MediaListItem item
            where item.list.id in :listIds
            group by item.list.id
            """)
    List<MediaListItemCount> countByListIds(@Param("listIds") Collection<UUID> listIds);

    long countByListId(UUID listId);

    long countByMediaIdAndListVisibility(UUID mediaId, Visibility visibility);

    @Query(value = """
            select item as item, item.list.id as listId, count(listLike) as likeCount
            from MediaListItem item
            left join MediaListLike listLike on listLike.list = item.list
            where item.media.id = :mediaId
              and item.list.visibility = :visibility
            group by item
            order by count(listLike) desc, max(item.list.updatedAt) desc, item.createdAt desc
            """, countQuery = """
            select count(item)
            from MediaListItem item
            where item.media.id = :mediaId
              and item.list.visibility = :visibility
            """)
    Page<MediaListPopularity> findAllByMediaIdAndListVisibility(
            @Param("mediaId") UUID mediaId,
            @Param("visibility") Visibility visibility,
            Pageable pageable
    );

    boolean existsByListIdAndMediaId(UUID listId, UUID mediaId);

    Optional<MediaListItem> findByIdAndListId(UUID id, UUID listId);

    @Query("""
            select coalesce(max(item.position), 0)
            from MediaListItem item
            where item.list.id = :listId
            """)
    int findMaxPositionByListId(@Param("listId") UUID listId);

    @Query("""
            select item
            from MediaListItem item
            join fetch item.media
            where item.list.id = :listId
            order by item.position asc, item.createdAt asc
            """)
    List<MediaListItem> findAllWithMediaByListId(@Param("listId") UUID listId);

    @Modifying(flushAutomatically = true)
    @Query("""
            update MediaListItem item
            set item.position = item.position - 1
            where item.list.id = :listId
              and item.position > :removedPosition
            """)
    void decrementPositionsAfter(
            @Param("listId") UUID listId,
            @Param("removedPosition") int removedPosition
    );

    interface MediaListItemCount {
        UUID getListId();

        long getItemCount();
    }

    interface MediaListPopularity {
        MediaListItem getItem();

        UUID getListId();

        long getLikeCount();
    }
}
