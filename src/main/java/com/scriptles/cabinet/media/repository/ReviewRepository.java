package com.scriptles.cabinet.media.repository;

import com.scriptles.cabinet.media.entity.Review;
import com.scriptles.cabinet.user.enums.Visibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {
    Optional<Review> findByAuthorProfileIdAndMediaId(UUID authorProfileId, UUID mediaId);
    org.springframework.data.domain.Page<Review> findAllByAuthorProfileIdAndContentIsNotNullOrderByPublishedAtDesc(
            UUID authorProfileId, org.springframework.data.domain.Pageable pageable);
    @EntityGraph(attributePaths = {"user", "media", "rating", "activity"})
    @Query("select r from Review r where r.user.id = :userId and r.media.id = :mediaId and r.authorProfile is null")
    Optional<Review> findByUserIdAndMediaId(@Param("userId") UUID userId, @Param("mediaId") UUID mediaId);

    Optional<Review> findByRatingId(UUID ratingId);

    @Query("""
            select review.media.id
            from Review review
            where review.user.id = :userId
              and review.media.id in :mediaIds
              and review.content is not null and trim(review.content) <> ''
            """)
    List<UUID> findReviewedMediaIds(
            @Param("userId") UUID userId,
            @Param("mediaIds") Collection<UUID> mediaIds
    );

    @Query("""
            select review.media.id
            from Review review
            where review.user.id = :userId
              and review.media.id in :mediaIds
              and review.visibility in :visibilities
              and review.content is not null and trim(review.content) <> ''
            """)
    List<UUID> findVisibleReviewedMediaIds(
            @Param("userId") UUID userId,
            @Param("mediaIds") Collection<UUID> mediaIds,
            @Param("visibilities") Collection<Visibility> visibilities
    );

    Optional<Review> findByActivityId(UUID activityId);

    @Query("select review from Review review join fetch review.activity activity "
            + "where activity.id in :activityIds and review.richContent is not null "
            + "and review.visibility in :visibilities")
    List<Review> findDiaryRichContent(
            @Param("activityIds") Collection<UUID> activityIds,
            @Param("visibilities") Collection<Visibility> visibilities
    );

    @Query("select review from Review review join fetch review.user join fetch review.media "
            + "where review.user.id in :userIds and review.media.id in :mediaIds "
            + "and review.authorProfile is null and review.richContent is not null "
            + "and review.visibility in :visibilities")
    List<Review> findRichContentForFeed(
            @Param("userIds") Collection<UUID> userIds,
            @Param("mediaIds") Collection<UUID> mediaIds,
            @Param("visibilities") Collection<Visibility> visibilities
    );

    @EntityGraph(attributePaths = {"user", "media", "rating", "activity"})
    @Query("""
            select review from Review review
            where review.user.id = :userId
              and review.authorProfile is null
              and review.content is not null
              and trim(review.content) <> ''
            order by coalesce(review.updatedAt, review.publishedAt, review.createdAt) desc,
                     review.id desc
            """)
    Page<Review> findMine(@Param("userId") UUID userId, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "media", "rating", "activity"})
    @Query("select r from Review r where r.id = :reviewId and r.visibility = :visibility "
            + "and r.content is not null and trim(r.content) <> ''")
    Optional<Review> findByIdAndVisibility(@Param("reviewId") UUID reviewId,
                                           @Param("visibility") Visibility visibility);

    @EntityGraph(attributePaths = {"user", "media", "rating", "activity"})
    @Query("select r from Review r where r.media.id = :mediaId and r.visibility = :visibility "
            + "and r.content is not null and trim(r.content) <> ''")
    Page<Review> findByMediaIdAndVisibility(@Param("mediaId") UUID mediaId,
                                            @Param("visibility") Visibility visibility,
                                            Pageable pageable);

    @EntityGraph(attributePaths = {"user", "media", "rating", "activity"})
    @Query(value = """
            select review from Review review
            where review.media.id = :mediaId
              and review.content is not null and trim(review.content) <> ''
              and (review.user.id = :viewerId
                or review.visibility = com.scriptles.cabinet.user.enums.Visibility.PUBLIC
                or (review.visibility = com.scriptles.cabinet.user.enums.Visibility.FOLLOWERS
                    and exists (select follow.id from UserFollow follow
                        where follow.follower.id = :viewerId
                          and follow.followed.id = review.user.id
                          and follow.status = com.scriptles.cabinet.user.enums.FollowStatus.ACCEPTED)))
              and not exists (select block.id from UserBlock block
                  where (block.blocker.id = :viewerId and block.blocked.id = review.user.id)
                     or (block.blocker.id = review.user.id and block.blocked.id = :viewerId))
            """, countQuery = """
            select count(review) from Review review
            where review.media.id = :mediaId
              and review.content is not null and trim(review.content) <> ''
              and (review.user.id = :viewerId
                or review.visibility = com.scriptles.cabinet.user.enums.Visibility.PUBLIC
                or (review.visibility = com.scriptles.cabinet.user.enums.Visibility.FOLLOWERS
                    and exists (select follow.id from UserFollow follow
                        where follow.follower.id = :viewerId
                          and follow.followed.id = review.user.id
                          and follow.status = com.scriptles.cabinet.user.enums.FollowStatus.ACCEPTED)))
              and not exists (select block.id from UserBlock block
                  where (block.blocker.id = :viewerId and block.blocked.id = review.user.id)
                     or (block.blocker.id = review.user.id and block.blocked.id = :viewerId))
            """)
    Page<Review> findAccessibleByMediaId(
            @Param("mediaId") UUID mediaId,
            @Param("viewerId") UUID viewerId,
            Pageable pageable);

    @Query("""
            select review.id
            from Review review
            left join ReviewLike reviewLike on reviewLike.review = review
            where review.media.id = :mediaId
              and review.visibility = :visibility
              and review.content is not null and trim(review.content) <> ''
            group by review.id, review.publishedAt, review.createdAt
            order by count(reviewLike.id) desc,
                     coalesce(review.publishedAt, review.createdAt) desc,
                     review.id desc
            """)
    List<UUID> findPopularIds(
            @Param("mediaId") UUID mediaId,
            @Param("visibility") Visibility visibility,
            Pageable pageable
    );

    @Query("""
            select review.id from Review review
            left join ReviewLike reviewLike on reviewLike.review = review
            where review.media.id = :mediaId
              and review.content is not null and trim(review.content) <> ''
              and (review.user.id = :viewerId
                or review.visibility = com.scriptles.cabinet.user.enums.Visibility.PUBLIC
                or (review.visibility = com.scriptles.cabinet.user.enums.Visibility.FOLLOWERS
                    and exists (select follow.id from UserFollow follow
                        where follow.follower.id = :viewerId and follow.followed.id = review.user.id
                          and follow.status = com.scriptles.cabinet.user.enums.FollowStatus.ACCEPTED)))
              and not exists (select block.id from UserBlock block
                  where (block.blocker.id = :viewerId and block.blocked.id = review.user.id)
                     or (block.blocker.id = review.user.id and block.blocked.id = :viewerId))
            group by review.id, review.publishedAt, review.createdAt
            order by count(reviewLike.id) desc,
              coalesce(review.publishedAt, review.createdAt) desc, review.id desc
            """)
    List<UUID> findAccessiblePopularIds(
            @Param("mediaId") UUID mediaId,
            @Param("viewerId") UUID viewerId,
            Pageable pageable);

    @Query("""
            select review.id
            from Review review
            left join ReviewLike reviewLike on reviewLike.review = review
            where review.visibility = :visibility
              and review.content is not null
              and trim(review.content) <> ''
            group by review.id, review.publishedAt, review.createdAt
            order by count(reviewLike.id) desc,
                     coalesce(review.publishedAt, review.createdAt) desc,
                     review.id desc
            """)
    List<UUID> findGloballyPopularIds(
            @Param("visibility") Visibility visibility,
            Pageable pageable
    );

    @Query("""
            select review.id from Review review
            left join ReviewLike reviewLike on reviewLike.review = review
            where review.content is not null and trim(review.content) <> ''
              and (review.user.id = :viewerId
                or review.visibility = com.scriptles.cabinet.user.enums.Visibility.PUBLIC
                or (review.visibility = com.scriptles.cabinet.user.enums.Visibility.FOLLOWERS
                    and exists (select follow.id from UserFollow follow
                        where follow.follower.id = :viewerId and follow.followed.id = review.user.id
                          and follow.status = com.scriptles.cabinet.user.enums.FollowStatus.ACCEPTED)))
              and not exists (select block.id from UserBlock block
                  where (block.blocker.id = :viewerId and block.blocked.id = review.user.id)
                     or (block.blocker.id = review.user.id and block.blocked.id = :viewerId))
            group by review.id, review.publishedAt, review.createdAt
            order by count(reviewLike.id) desc,
              coalesce(review.publishedAt, review.createdAt) desc, review.id desc
            """)
    List<UUID> findGloballyAccessiblePopularIds(
            @Param("viewerId") UUID viewerId,
            Pageable pageable);

    @EntityGraph(attributePaths = {"user", "media", "rating", "activity"})
    List<Review> findAllByIdIn(Collection<UUID> reviewIds);

    @EntityGraph(attributePaths = {"user", "media", "rating", "activity"})
    @Query("""
            select review from Review review
            where review.media.id = :mediaId and review.visibility = :visibility
              and review.content is not null and trim(review.content) <> ''
            order by coalesce(review.publishedAt, review.createdAt) desc, review.id desc
            """)
    List<Review> findRecent(
            @Param("mediaId") UUID mediaId,
            @Param("visibility") Visibility visibility,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"user", "media", "rating", "activity"})
    @Query("""
            select review from Review review
            where review.media.id = :mediaId
              and review.content is not null and trim(review.content) <> ''
              and (review.user.id = :viewerId
                or review.visibility = com.scriptles.cabinet.user.enums.Visibility.PUBLIC
                or (review.visibility = com.scriptles.cabinet.user.enums.Visibility.FOLLOWERS
                    and exists (select follow.id from UserFollow follow
                        where follow.follower.id = :viewerId and follow.followed.id = review.user.id
                          and follow.status = com.scriptles.cabinet.user.enums.FollowStatus.ACCEPTED)))
              and not exists (select block.id from UserBlock block
                  where (block.blocker.id = :viewerId and block.blocked.id = review.user.id)
                     or (block.blocker.id = review.user.id and block.blocked.id = :viewerId))
            order by coalesce(review.publishedAt, review.createdAt) desc, review.id desc
            """)
    List<Review> findAccessibleRecent(
            @Param("mediaId") UUID mediaId,
            @Param("viewerId") UUID viewerId,
            Pageable pageable);

    default List<Review> findTop3ByRatingMediaIdAndRatingVisibilityOrderByCreatedAtDescIdDesc(
            UUID mediaId,
            Visibility visibility
    ) {
        return findRecent(mediaId, visibility, PageRequest.of(0, 3));
    }
}
