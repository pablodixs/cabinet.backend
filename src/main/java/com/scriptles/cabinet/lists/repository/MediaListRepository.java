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

    List<MediaList> findAllByHqProfileIdOrderByUpdatedAtDesc(UUID hqProfileId);

    Optional<MediaList> findByIdAndHqProfileId(UUID id, UUID hqProfileId);

    Optional<MediaList> findByIdAndOwnerId(UUID id, UUID ownerId);

    Optional<MediaList> findByOwnerIdAndOriginSourceAndOriginKey(
            UUID ownerId,
            ExternalSource originSource,
            String originKey
    );

    @Query("""
            select tag.id as tagId, count(list.id) as usageCount
            from MediaList list
            join list.tags tag
            where list.owner.id = :ownerId
            group by tag.id
            """)
    List<TagUsageCount> countTagUsageByOwnerId(
            @Param("ownerId") UUID ownerId);

    interface TagUsageCount {
        UUID getTagId();
        long getUsageCount();
    }

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

    @Query(value = """
            select list
            from MediaList list
            join fetch list.owner
            where (list.visibility = com.scriptles.cabinet.user.enums.Visibility.PUBLIC
                or list.owner.id = :viewerId
                or (list.visibility = com.scriptles.cabinet.user.enums.Visibility.FOLLOWERS
                    and exists (select follow.id from UserFollow follow
                        where follow.follower.id = :viewerId
                          and follow.followed.id = list.owner.id
                          and follow.status = com.scriptles.cabinet.user.enums.FollowStatus.ACCEPTED)))
              and not exists (select block.id from UserBlock block
                  where (block.blocker.id = :viewerId and block.blocked.id = list.owner.id)
                     or (block.blocker.id = list.owner.id and block.blocked.id = :viewerId))
              and (lower(list.name) like lower(concat('%', :query, '%'))
                or lower(coalesce(list.description, '')) like lower(concat('%', :query, '%')))
            order by case
                when lower(list.name) = lower(:query) then 0
                when lower(list.name) like lower(concat(:query, '%')) then 1
                else 2 end,
              list.updatedAt desc, list.name asc
            """, countQuery = """
            select count(list) from MediaList list
            where (list.visibility = com.scriptles.cabinet.user.enums.Visibility.PUBLIC
                or list.owner.id = :viewerId
                or (list.visibility = com.scriptles.cabinet.user.enums.Visibility.FOLLOWERS
                    and exists (select follow.id from UserFollow follow
                        where follow.follower.id = :viewerId
                          and follow.followed.id = list.owner.id
                          and follow.status = com.scriptles.cabinet.user.enums.FollowStatus.ACCEPTED)))
              and not exists (select block.id from UserBlock block
                  where (block.blocker.id = :viewerId and block.blocked.id = list.owner.id)
                     or (block.blocker.id = list.owner.id and block.blocked.id = :viewerId))
              and (lower(list.name) like lower(concat('%', :query, '%'))
                or lower(coalesce(list.description, '')) like lower(concat('%', :query, '%')))
            """)
    Page<MediaList> searchAccessibleLists(
            @Param("query") String query,
            @Param("viewerId") UUID viewerId,
            Pageable pageable
    );

    @Query(value = """
            select list
            from MediaList list
            join fetch list.owner
            where list.owner.id = :ownerId
              and (
                list.owner.id = :viewerId
                or list.visibility = com.scriptles.cabinet.user.enums.Visibility.PUBLIC
                or (:viewerId is not null
                    and list.visibility = com.scriptles.cabinet.user.enums.Visibility.FOLLOWERS
                    and exists (select follow.id from UserFollow follow
                        where follow.follower.id = :viewerId
                          and follow.followed.id = :ownerId
                          and follow.status = com.scriptles.cabinet.user.enums.FollowStatus.ACCEPTED))
              )
            order by list.updatedAt desc, list.createdAt desc, list.id desc
            """, countQuery = """
            select count(list)
            from MediaList list
            where list.owner.id = :ownerId
              and (
                list.owner.id = :viewerId
                or list.visibility = com.scriptles.cabinet.user.enums.Visibility.PUBLIC
                or (:viewerId is not null
                    and list.visibility = com.scriptles.cabinet.user.enums.Visibility.FOLLOWERS
                    and exists (select follow.id from UserFollow follow
                        where follow.follower.id = :viewerId
                          and follow.followed.id = :ownerId
                          and follow.status = com.scriptles.cabinet.user.enums.FollowStatus.ACCEPTED))
              )
            """)
    Page<MediaList> findAccessibleByOwner(
            @Param("ownerId") UUID ownerId,
            @Param("viewerId") UUID viewerId,
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

    @Query("""
            select list.id as listId, count(listLike.id) as likeCount
            from MediaList list
            left join MediaListLike listLike on listLike.list = list
            where (list.visibility = com.scriptles.cabinet.user.enums.Visibility.PUBLIC
                or list.owner.id = :viewerId
                or (list.visibility = com.scriptles.cabinet.user.enums.Visibility.FOLLOWERS
                    and exists (select follow.id from UserFollow follow
                        where follow.follower.id = :viewerId
                          and follow.followed.id = list.owner.id
                          and follow.status = com.scriptles.cabinet.user.enums.FollowStatus.ACCEPTED)))
              and not exists (select block.id from UserBlock block
                  where (block.blocker.id = :viewerId and block.blocked.id = list.owner.id)
                     or (block.blocker.id = list.owner.id and block.blocked.id = :viewerId))
            group by list.id, list.updatedAt
            order by count(listLike.id) desc, list.updatedAt desc, list.id desc
            """)
    List<PopularListProjection> findPopularAccessibleLists(
            @Param("viewerId") UUID viewerId,
            Pageable pageable
    );

    interface PopularListProjection {
        UUID getListId();

        long getLikeCount();
    }
}
