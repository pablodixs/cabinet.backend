package com.scriptles.cabinet.lists.repository;

import com.scriptles.cabinet.lists.entity.MediaList;
import com.scriptles.cabinet.user.enums.Visibility;
import com.scriptles.cabinet.media.enums.ExternalSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    Optional<MediaList> findByOwnerIdAndOriginSourceAndOriginKey(
            UUID ownerId,
            ExternalSource originSource,
            String originKey
    );

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

    @Query(value = """
            select list
            from MediaList list
            join fetch list.owner
            where list.visibility = :visibility
              and (
                lower(list.name) like lower(concat('%', :query, '%'))
                or lower(coalesce(list.description, '')) like lower(concat('%', :query, '%'))
              )
            order by
              case
                when lower(list.name) = lower(:query) then 0
                when lower(list.name) like lower(concat(:query, '%')) then 1
                else 2
              end,
              list.updatedAt desc,
              list.name asc
            """, countQuery = """
            select count(list)
            from MediaList list
            where list.visibility = :visibility
              and (
                lower(list.name) like lower(concat('%', :query, '%'))
                or lower(coalesce(list.description, '')) like lower(concat('%', :query, '%'))
              )
            """)
    Page<MediaList> searchPublicLists(
            @Param("query") String query,
            @Param("visibility") Visibility visibility,
            Pageable pageable
    );

    @Query("""
            select list.id as listId, count(listLike.id) as likeCount
            from MediaList list
            left join MediaListLike listLike on listLike.list = list
            where list.visibility = :visibility
            group by list.id, list.updatedAt
            order by count(listLike.id) desc, list.updatedAt desc, list.id desc
            """)
    List<PopularListProjection> findPopularPublicLists(
            @Param("visibility") Visibility visibility,
            Pageable pageable
    );

    interface PopularListProjection {
        UUID getListId();

        long getLikeCount();
    }
}
