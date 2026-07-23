package com.scriptles.cabinet.user.repository;

import com.scriptles.cabinet.user.entity.UserFollow;
import com.scriptles.cabinet.user.entity.UserFollowId;
import com.scriptles.cabinet.user.enums.FollowStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserFollowRepository extends JpaRepository<UserFollow, UserFollowId> {
    boolean existsByFollowerIdAndFollowedIdAndStatus(
            UUID followerId, UUID followedId, FollowStatus status);

    Optional<UserFollow> findByFollowerIdAndFollowedId(UUID followerId, UUID followedId);

    @EntityGraph(attributePaths = "follower")
    @Query("""
            select follow from UserFollow follow
            where follow.followed.id = :userId
              and follow.status = com.scriptles.cabinet.user.enums.FollowStatus.ACCEPTED
              and not exists (
                  select block.id from UserBlock block
                  where (block.blocker.id = :viewerId and block.blocked.id = follow.follower.id)
                     or (block.blocker.id = follow.follower.id and block.blocked.id = :viewerId)
              )
            order by follow.acceptedAt desc, follow.follower.id desc
            """)
    List<UserFollow> findFirstFollowers(
            @Param("userId") UUID userId,
            @Param("viewerId") UUID viewerId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "follower")
    @Query("""
            select follow from UserFollow follow
            where follow.followed.id = :userId
              and follow.status = com.scriptles.cabinet.user.enums.FollowStatus.ACCEPTED
              and (follow.acceptedAt < :cursorTime
                   or (follow.acceptedAt = :cursorTime and follow.follower.id < :cursorUserId))
              and not exists (
                  select block.id from UserBlock block
                  where (block.blocker.id = :viewerId and block.blocked.id = follow.follower.id)
                     or (block.blocker.id = follow.follower.id and block.blocked.id = :viewerId)
              )
            order by follow.acceptedAt desc, follow.follower.id desc
            """)
    List<UserFollow> findFollowersAfter(
            @Param("userId") UUID userId,
            @Param("viewerId") UUID viewerId,
            @Param("cursorTime") Instant cursorTime,
            @Param("cursorUserId") UUID cursorUserId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "followed")
    @Query("""
            select follow from UserFollow follow
            where follow.follower.id = :userId
              and follow.status = com.scriptles.cabinet.user.enums.FollowStatus.ACCEPTED
              and not exists (
                  select block.id from UserBlock block
                  where (block.blocker.id = :viewerId and block.blocked.id = follow.followed.id)
                     or (block.blocker.id = follow.followed.id and block.blocked.id = :viewerId)
              )
            order by follow.acceptedAt desc, follow.followed.id desc
            """)
    List<UserFollow> findFirstFollowing(
            @Param("userId") UUID userId,
            @Param("viewerId") UUID viewerId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "followed")
    @Query("""
            select follow from UserFollow follow
            where follow.follower.id = :userId
              and follow.status = com.scriptles.cabinet.user.enums.FollowStatus.ACCEPTED
              and (follow.acceptedAt < :cursorTime
                   or (follow.acceptedAt = :cursorTime and follow.followed.id < :cursorUserId))
              and not exists (
                  select block.id from UserBlock block
                  where (block.blocker.id = :viewerId and block.blocked.id = follow.followed.id)
                     or (block.blocker.id = follow.followed.id and block.blocked.id = :viewerId)
              )
            order by follow.acceptedAt desc, follow.followed.id desc
            """)
    List<UserFollow> findFollowingAfter(
            @Param("userId") UUID userId,
            @Param("viewerId") UUID viewerId,
            @Param("cursorTime") Instant cursorTime,
            @Param("cursorUserId") UUID cursorUserId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "follower")
    @Query("""
            select follow from UserFollow follow
            where follow.followed.id = :userId
              and follow.status = com.scriptles.cabinet.user.enums.FollowStatus.PENDING
            order by follow.requestedAt desc, follow.follower.id desc
            """)
    List<UserFollow> findFirstIncomingRequests(
            @Param("userId") UUID userId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "follower")
    @Query("""
            select follow from UserFollow follow
            where follow.followed.id = :userId
              and follow.status = com.scriptles.cabinet.user.enums.FollowStatus.PENDING
              and (follow.requestedAt < :cursorTime
                   or (follow.requestedAt = :cursorTime and follow.follower.id < :cursorUserId))
            order by follow.requestedAt desc, follow.follower.id desc
            """)
    List<UserFollow> findIncomingRequestsAfter(
            @Param("userId") UUID userId,
            @Param("cursorTime") Instant cursorTime,
            @Param("cursorUserId") UUID cursorUserId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "followed")
    @Query("""
            select follow from UserFollow follow
            where follow.follower.id = :userId
              and follow.status = com.scriptles.cabinet.user.enums.FollowStatus.PENDING
            order by follow.requestedAt desc, follow.followed.id desc
            """)
    List<UserFollow> findFirstOutgoingRequests(
            @Param("userId") UUID userId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "followed")
    @Query("""
            select follow from UserFollow follow
            where follow.follower.id = :userId
              and follow.status = com.scriptles.cabinet.user.enums.FollowStatus.PENDING
              and (follow.requestedAt < :cursorTime
                   or (follow.requestedAt = :cursorTime and follow.followed.id < :cursorUserId))
            order by follow.requestedAt desc, follow.followed.id desc
            """)
    List<UserFollow> findOutgoingRequestsAfter(
            @Param("userId") UUID userId,
            @Param("cursorTime") Instant cursorTime,
            @Param("cursorUserId") UUID cursorUserId,
            Pageable pageable
    );

    @Query("""
            select follow.followed.id as userId, follow.status as status
            from UserFollow follow
            where follow.follower.id = :viewerId and follow.followed.id in :userIds
            """)
    List<OutgoingStateProjection> findOutgoingStates(
            @Param("viewerId") UUID viewerId,
            @Param("userIds") Collection<UUID> userIds
    );

    @Query("""
            select follow.follower.id
            from UserFollow follow
            where follow.followed.id = :viewerId
              and follow.follower.id in :userIds
              and follow.status = com.scriptles.cabinet.user.enums.FollowStatus.ACCEPTED
            """)
    List<UUID> findUsersFollowingViewer(
            @Param("viewerId") UUID viewerId,
            @Param("userIds") Collection<UUID> userIds
    );

    interface OutgoingStateProjection {
        UUID getUserId();
        FollowStatus getStatus();
    }
}
