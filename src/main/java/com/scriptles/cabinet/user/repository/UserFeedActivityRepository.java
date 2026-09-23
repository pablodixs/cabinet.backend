package com.scriptles.cabinet.user.repository;

import com.scriptles.cabinet.user.entity.UserFeedActivity;
import com.scriptles.cabinet.user.enums.FeedActionType;
import com.scriptles.cabinet.user.enums.Visibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.Collection;
import java.util.UUID;

public interface UserFeedActivityRepository extends JpaRepository<UserFeedActivity, UUID> {
    Optional<UserFeedActivity> findByUserIdAndMediaIdAndActionType(UUID userId, UUID mediaId, FeedActionType actionType);
    void deleteByUserIdAndMediaIdAndActionType(UUID userId, UUID mediaId, FeedActionType actionType);

    @Query(value = """
            select activity from UserFeedActivity activity
            join fetch activity.user actor
            join fetch activity.media media
            where actor.active = true
              and ((:friendsOnly = false and actor.id = :viewerId)
                or (:friendsOnly = true and activity.visibility in :friendVisibilities
                    and exists (select follow.id from UserFollow follow
                        where follow.follower.id = :viewerId and follow.followed.id = actor.id
                          and follow.status = com.scriptles.cabinet.user.enums.FollowStatus.ACCEPTED)))
              and (:friendsOnly = false or not exists (select block.id from UserBlock block
                    where (block.blocker.id = :viewerId and block.blocked.id = actor.id)
                       or (block.blocker.id = actor.id and block.blocked.id = :viewerId)))
            order by activity.occurredAt desc, activity.id desc
            """, countQuery = """
            select count(activity) from UserFeedActivity activity
            where activity.user.active = true
              and ((:friendsOnly = false and activity.user.id = :viewerId)
                or (:friendsOnly = true and activity.visibility in :friendVisibilities
                    and exists (select follow.id from UserFollow follow
                        where follow.follower.id = :viewerId and follow.followed.id = activity.user.id
                          and follow.status = com.scriptles.cabinet.user.enums.FollowStatus.ACCEPTED)))
              and (:friendsOnly = false or not exists (select block.id from UserBlock block
                    where (block.blocker.id = :viewerId and block.blocked.id = activity.user.id)
                       or (block.blocker.id = activity.user.id and block.blocked.id = :viewerId)))
            """)
    Page<UserFeedActivity> findFeed(
            @Param("viewerId") UUID viewerId,
            @Param("friendsOnly") boolean friendsOnly,
            @Param("friendVisibilities") Collection<Visibility> friendVisibilities,
            Pageable pageable);

    @Query(value = """
            select activity from UserFeedActivity activity
            join fetch activity.user actor
            join fetch activity.media media
            where media.id = :mediaId and actor.active = true
              and activity.visibility in :friendVisibilities
              and exists (select follow.id from UserFollow follow
                    where follow.follower.id = :viewerId and follow.followed.id = actor.id
                      and follow.status = com.scriptles.cabinet.user.enums.FollowStatus.ACCEPTED)
              and not exists (select block.id from UserBlock block
                    where (block.blocker.id = :viewerId and block.blocked.id = actor.id)
                       or (block.blocker.id = actor.id and block.blocked.id = :viewerId))
            order by activity.occurredAt desc, activity.id desc
            """, countQuery = """
            select count(activity) from UserFeedActivity activity
            where activity.media.id = :mediaId and activity.user.active = true
              and activity.visibility in :friendVisibilities
              and exists (select follow.id from UserFollow follow
                    where follow.follower.id = :viewerId and follow.followed.id = activity.user.id
                      and follow.status = com.scriptles.cabinet.user.enums.FollowStatus.ACCEPTED)
              and not exists (select block.id from UserBlock block
                    where (block.blocker.id = :viewerId and block.blocked.id = activity.user.id)
                       or (block.blocker.id = activity.user.id and block.blocked.id = :viewerId))
            """)
    Page<UserFeedActivity> findFriendsActivityForMedia(
            @Param("viewerId") UUID viewerId,
            @Param("mediaId") UUID mediaId,
            @Param("friendVisibilities") Collection<Visibility> friendVisibilities,
            Pageable pageable);
}
