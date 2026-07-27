package com.scriptles.cabinet.lists.repository;

import com.scriptles.cabinet.lists.entity.MediaListItem;
import com.scriptles.cabinet.user.enums.Visibility;
import com.scriptles.cabinet.user.enums.UserMediaStatus;
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

    @Query("""
            select item.list.id as listId, count(item) as consumedItemCount
            from MediaListItem item
            join UserMedia userMedia
              on userMedia.media = item.media
             and userMedia.user.id = :userId
            where item.list.id in :listIds
              and userMedia.status = :status
            group by item.list.id
            """)
    List<MediaListConsumptionCount> countConsumedByListIds(
            @Param("userId") UUID userId,
            @Param("listIds") Collection<UUID> listIds,
            @Param("status") UserMediaStatus status
    );

    @Query("""
            select item.media.id
            from MediaListItem item
            join UserMedia userMedia
              on userMedia.media = item.media
             and userMedia.user.id = :userId
            where item.list.id = :listId
              and userMedia.status = :status
            """)
    List<UUID> findConsumedMediaIds(
            @Param("userId") UUID userId,
            @Param("listId") UUID listId,
            @Param("status") UserMediaStatus status
    );

    @Query(value = """
            select cast(ranked.list_id as varchar) as "listIdValue",
                   cast(ranked.media_id as varchar) as "mediaIdValue",
                   ranked.cover_url as "coverUrl",
                   ranked.media_type as "typeValue"
            from (
                select item.list_id,
                       media.id as media_id,
                       media.cover_url,
                       cast(media.type as varchar) as media_type,
                       row_number() over (
                           partition by item.list_id
                           order by item.created_at desc, item.position desc, item.id desc
                       ) as cover_position
                from media_list_items item
                join media on media.id = item.media_id
                join media_lists list on list.id = item.list_id
                join users owner on owner.id = list.owner_id
                left join user_media_artwork_preferences artwork
                  on artwork.user_id = list.owner_id
                 and artwork.media_id = item.media_id
                where item.list_id in (:listIds)
                  and (media.cover_url is not null
                    or (owner.account_tier = 'PRO' and artwork.cover_url is not null))
            ) ranked
            where ranked.cover_position <= 4
            order by ranked.list_id, ranked.cover_position
            """, nativeQuery = true)
    List<MediaListCover> findRecentCoversByListIds(@Param("listIds") Collection<UUID> listIds);

    long countByListId(UUID listId);

    long deleteByListId(UUID listId);

    long countByMediaIdAndListVisibility(UUID mediaId, Visibility visibility);

    @Query("""
            select item.list.id
            from MediaListItem item
            where item.media.id = :mediaId
              and item.list.owner.id = :ownerId
            """)
    List<UUID> findListIdsByMediaIdAndOwnerId(
            @Param("mediaId") UUID mediaId,
            @Param("ownerId") UUID ownerId
    );

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

    @Query(value = """
            select item as item, item.list.id as listId, count(listLike) as likeCount
            from MediaListItem item
            left join MediaListLike listLike on listLike.list = item.list
            where item.media.id = :mediaId
              and (item.list.visibility = com.scriptles.cabinet.user.enums.Visibility.PUBLIC
                or item.list.owner.id = :viewerId
                or (item.list.visibility = com.scriptles.cabinet.user.enums.Visibility.FOLLOWERS
                    and exists (select follow.id from UserFollow follow
                        where follow.follower.id = :viewerId
                          and follow.followed.id = item.list.owner.id
                          and follow.status = com.scriptles.cabinet.user.enums.FollowStatus.ACCEPTED)))
              and not exists (select block.id from UserBlock block
                  where (block.blocker.id = :viewerId and block.blocked.id = item.list.owner.id)
                     or (block.blocker.id = item.list.owner.id and block.blocked.id = :viewerId))
            group by item
            order by count(listLike) desc, max(item.list.updatedAt) desc, item.createdAt desc
            """, countQuery = """
            select count(item) from MediaListItem item
            where item.media.id = :mediaId
              and (item.list.visibility = com.scriptles.cabinet.user.enums.Visibility.PUBLIC
                or item.list.owner.id = :viewerId
                or (item.list.visibility = com.scriptles.cabinet.user.enums.Visibility.FOLLOWERS
                    and exists (select follow.id from UserFollow follow
                        where follow.follower.id = :viewerId
                          and follow.followed.id = item.list.owner.id
                          and follow.status = com.scriptles.cabinet.user.enums.FollowStatus.ACCEPTED)))
              and not exists (select block.id from UserBlock block
                  where (block.blocker.id = :viewerId and block.blocked.id = item.list.owner.id)
                     or (block.blocker.id = item.list.owner.id and block.blocked.id = :viewerId))
            """)
    Page<MediaListPopularity> findAllAccessibleByMediaId(
            @Param("mediaId") UUID mediaId,
            @Param("viewerId") UUID viewerId,
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

    interface MediaListConsumptionCount {
        UUID getListId();

        long getConsumedItemCount();
    }

    interface MediaListCover {
        String getListIdValue();

        default UUID getListId() {
            return UUID.fromString(getListIdValue());
        }

        String getCoverUrl();

        String getMediaIdValue();

        default UUID getMediaId() {
            return UUID.fromString(getMediaIdValue());
        }

        String getTypeValue();

        default com.scriptles.cabinet.media.enums.MediaType getType() {
            return com.scriptles.cabinet.media.enums.MediaType.valueOf(getTypeValue());
        }
    }

    interface MediaListPopularity {
        MediaListItem getItem();

        UUID getListId();

        long getLikeCount();
    }
}
