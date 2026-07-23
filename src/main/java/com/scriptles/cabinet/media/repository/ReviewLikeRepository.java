package com.scriptles.cabinet.media.repository;

import com.scriptles.cabinet.media.entity.ReviewLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewLikeRepository extends JpaRepository<ReviewLike, UUID> {
    @Modifying
    @Query(value = """
            insert into review_likes (id, user_id, review_id, created_at)
            values (:id, :userId, :reviewId, current_timestamp)
            on conflict (user_id, review_id) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("userId") UUID userId,
            @Param("reviewId") UUID reviewId
    );

    boolean existsByUserIdAndReviewId(UUID userId, UUID reviewId);

    long countByReviewId(UUID reviewId);

    long countByReviewIdAndUserIdNot(UUID reviewId, UUID userId);

    Optional<ReviewLike> findFirstByReviewIdAndUserIdNotOrderByCreatedAtDescIdDesc(
            UUID reviewId, UUID userId);

    long deleteByUserIdAndReviewId(UUID userId, UUID reviewId);

    long deleteByReviewId(UUID reviewId);

    @Query("""
            select reviewLike.review.id as reviewId,
                   count(reviewLike.id) as likeCount
            from ReviewLike reviewLike
            where reviewLike.review.id in :reviewIds
            group by reviewLike.review.id
            """)
    List<ReviewLikeCount> countByReviewIds(@Param("reviewIds") Collection<UUID> reviewIds);

    @Query("""
            select reviewLike.review.id
            from ReviewLike reviewLike
            where reviewLike.user.id = :userId
              and reviewLike.review.id in :reviewIds
            """)
    List<UUID> findLikedReviewIds(
            @Param("userId") UUID userId,
            @Param("reviewIds") Collection<UUID> reviewIds
    );

    @Query(value = """
            select cast(ranked.review_id as varchar) as "reviewId",
                   cast(ranked.user_id as varchar) as "userId",
                   ranked.username as "username",
                   ranked.avatar_url as "avatarUrl",
                   ranked.account_tier as "accountTier"
            from (
                select review_like.review_id,
                       review_like.user_id,
                       reviewer.username,
                       reviewer.avatar_ulr as avatar_url,
                       reviewer.account_tier,
                       row_number() over (
                           partition by review_like.review_id
                           order by review_like.created_at desc, review_like.id desc
                       ) as liker_position
                from review_likes review_like
                join users reviewer on reviewer.id = review_like.user_id
                where review_like.review_id in (:reviewIds)
            ) ranked
            where ranked.liker_position <= 5
            order by ranked.review_id, ranked.liker_position
            """, nativeQuery = true)
    List<RecentReviewLiker> findRecentLikers(@Param("reviewIds") Collection<UUID> reviewIds);

    @Query(value = """
            select cast(ranked.review_id as varchar) as "reviewId",
                   cast(ranked.user_id as varchar) as "userId",
                   ranked.username as "username",
                   ranked.avatar_url as "avatarUrl",
                   ranked.account_tier as "accountTier"
            from (
                select review_like.review_id,
                       review_like.user_id,
                       reviewer.username,
                       reviewer.avatar_ulr as avatar_url,
                       reviewer.account_tier,
                       row_number() over (
                           partition by review_like.review_id
                           order by review_like.created_at desc, review_like.id desc
                       ) as liker_position
                from review_likes review_like
                join users reviewer on reviewer.id = review_like.user_id
                where review_like.review_id in (:reviewIds)
                  and not exists (
                      select 1 from user_blocks block
                      where (block.blocker_id = :viewerId and block.blocked_id = review_like.user_id)
                         or (block.blocker_id = review_like.user_id and block.blocked_id = :viewerId)
                  )
            ) ranked
            where ranked.liker_position <= 5
            order by ranked.review_id, ranked.liker_position
            """, nativeQuery = true)
    List<RecentReviewLiker> findRecentLikersVisibleTo(
            @Param("reviewIds") Collection<UUID> reviewIds,
            @Param("viewerId") UUID viewerId);

    interface ReviewLikeCount {
        UUID getReviewId();

        long getLikeCount();
    }

    interface RecentReviewLiker {
        String getReviewId();

        String getUserId();

        String getUsername();

        String getAvatarUrl();

        String getAccountTier();
    }
}
