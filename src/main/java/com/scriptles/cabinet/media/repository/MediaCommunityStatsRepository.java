package com.scriptles.cabinet.media.repository;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MediaCommunityStatsRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public MediaCommunityStatsRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<CommunityStats> findByMediaId(UUID mediaId) {
        return jdbc.query("""
                select rating_count, rating_sum, average_rating, like_count, list_count, completed_count
                from media_community_stats
                where media_id = :mediaId
                """, new MapSqlParameterSource("mediaId", mediaId), STATS_ROW_MAPPER)
                .stream().findFirst();
    }

    public List<RatingBucket> findDistribution(UUID mediaId) {
        return jdbc.query("""
                select rating, rating_count
                from media_rating_distribution
                where media_id = :mediaId
                order by rating
                """, new MapSqlParameterSource("mediaId", mediaId), BUCKET_ROW_MAPPER);
    }

    public CanonicalStats readCanonicalState(UUID mediaId) {
        return jdbc.queryForObject("""
                select
                    (select count(*) from ratings r
                     where r.media_id = :mediaId and r.visibility = 'PUBLIC') as rating_count,
                    coalesce((select sum(r.rating) from ratings r
                              where r.media_id = :mediaId and r.visibility = 'PUBLIC'), 0) as rating_sum,
                    (select count(*) from media_likes l
                     where l.media_id = :mediaId) as like_count,
                    (select count(*) from media_list_items i
                     join media_lists ml on ml.id = i.list_id
                     where i.media_id = :mediaId and ml.visibility = 'PUBLIC') as list_count,
                    (select count(*) from user_media um
                     where um.media_id = :mediaId
                       and um.status = 'COMPLETED'
                       and um.private_entry = false) as completed_count
                """, new MapSqlParameterSource("mediaId", mediaId), CANONICAL_ROW_MAPPER);
    }

    public CommunityAggregate aggregateByMediaIds(Collection<UUID> mediaIds) {
        if (mediaIds.isEmpty()) return new CommunityAggregate(BigDecimal.ZERO, 0);
        return jdbc.queryForObject("""
                select coalesce(sum(rating_sum), 0) as rating_sum,
                       coalesce(sum(rating_count), 0) as rating_count
                from media_community_stats
                where media_id in (:mediaIds)
                """, new MapSqlParameterSource("mediaIds", mediaIds), AGGREGATE_ROW_MAPPER);
    }

    public List<RatingBucket> aggregateDistributionByMediaIds(Collection<UUID> mediaIds) {
        if (mediaIds.isEmpty()) return List.of();
        return jdbc.query("""
                select rating, sum(rating_count) as rating_count
                from media_rating_distribution
                where media_id in (:mediaIds)
                group by rating
                order by rating
                """, new MapSqlParameterSource("mediaIds", mediaIds), BUCKET_ROW_MAPPER);
    }

    public void upsert(UUID mediaId, CanonicalStats counts, BigDecimal averageRating) {
        jdbc.update("""
                insert into media_community_stats (
                    media_id, rating_count, rating_sum, average_rating,
                    like_count, list_count, completed_count, updated_at
                ) values (
                    :mediaId, :ratingCount, :ratingSum, :averageRating,
                    :likeCount, :listCount, :completedCount, current_timestamp
                )
                on conflict (media_id) do update set
                    rating_count = excluded.rating_count,
                    rating_sum = excluded.rating_sum,
                    average_rating = excluded.average_rating,
                    like_count = excluded.like_count,
                    list_count = excluded.list_count,
                    completed_count = excluded.completed_count,
                    updated_at = excluded.updated_at
                """, new MapSqlParameterSource()
                .addValue("mediaId", mediaId)
                .addValue("ratingCount", counts.ratingCount())
                .addValue("ratingSum", counts.ratingSum())
                .addValue("averageRating", averageRating)
                .addValue("likeCount", counts.likeCount())
                .addValue("listCount", counts.listCount())
                .addValue("completedCount", counts.completedCount()));
    }

    public void replaceDistribution(UUID mediaId) {
        jdbc.update("delete from media_rating_distribution where media_id = :mediaId",
                new MapSqlParameterSource("mediaId", mediaId));
        jdbc.update("""
                insert into media_rating_distribution (media_id, rating, rating_count)
                select r.media_id, r.rating, count(*)
                from ratings r
                where r.media_id = :mediaId and r.visibility = 'PUBLIC'
                group by r.media_id, r.rating
                """, new MapSqlParameterSource("mediaId", mediaId));
    }

    public void markDirty(UUID mediaId) {
        jdbc.update("""
                insert into media_community_stats_dirty (media_id, dirty_version, marked_at)
                values (:mediaId, 1, current_timestamp)
                on conflict (media_id) do update set
                    dirty_version = media_community_stats_dirty.dirty_version + 1,
                    marked_at = current_timestamp
                """, new MapSqlParameterSource("mediaId", mediaId));
    }

    public Optional<Long> lockDirtyVersion(UUID mediaId) {
        return jdbc.query("""
                select dirty_version
                from media_community_stats_dirty
                where media_id = :mediaId
                for update
                """, new MapSqlParameterSource("mediaId", mediaId),
                (rs, rowNum) -> rs.getLong("dirty_version")).stream().findFirst();
    }

    public void clearDirty(UUID mediaId, long dirtyVersion) {
        jdbc.update("""
                delete from media_community_stats_dirty
                where media_id = :mediaId and dirty_version = :dirtyVersion
                """, new MapSqlParameterSource()
                .addValue("mediaId", mediaId)
                .addValue("dirtyVersion", dirtyVersion));
    }

    public List<UUID> findDirtyMediaIds(int limit) {
        return findMediaIds("""
                select media_id from media_community_stats_dirty
                order by marked_at, media_id limit :limit
                """, new MapSqlParameterSource("limit", limit));
    }

    public List<UUID> findMissingMediaIds(int limit) {
        return findMediaIds("""
                select m.id as media_id
                from media m
                left join media_community_stats s on s.media_id = m.id
                where s.media_id is null
                order by m.id
                limit :limit
                """, new MapSqlParameterSource("limit", limit));
    }

    public List<UUID> findStaleMediaIds(java.time.Instant updatedBefore, int limit) {
        return findMediaIds("""
                select media_id
                from media_community_stats
                where updated_at < :updatedBefore
                order by updated_at, media_id
                limit :limit
                """, new MapSqlParameterSource()
                .addValue("updatedBefore", java.sql.Timestamp.from(updatedBefore))
                .addValue("limit", limit));
    }

    private List<UUID> findMediaIds(String sql, MapSqlParameterSource parameters) {
        return jdbc.query(sql, parameters, (rs, rowNum) -> rs.getObject("media_id", UUID.class));
    }

    private static final RowMapper<CommunityStats> STATS_ROW_MAPPER = (rs, rowNum) ->
            new CommunityStats(
                    rs.getLong("rating_count"),
                    rs.getBigDecimal("rating_sum"),
                    rs.getBigDecimal("average_rating"),
                    rs.getLong("like_count"),
                    rs.getLong("list_count"),
                    rs.getLong("completed_count"));

    private static final RowMapper<RatingBucket> BUCKET_ROW_MAPPER = (rs, rowNum) ->
            new RatingBucket(rs.getBigDecimal("rating"), rs.getLong("rating_count"));

    private static final RowMapper<CanonicalStats> CANONICAL_ROW_MAPPER = (rs, rowNum) ->
            new CanonicalStats(
                    rs.getLong("rating_count"),
                    rs.getBigDecimal("rating_sum"),
                    rs.getLong("like_count"),
                    rs.getLong("list_count"),
                    rs.getLong("completed_count"));

    private static final RowMapper<CommunityAggregate> AGGREGATE_ROW_MAPPER = (rs, rowNum) ->
            new CommunityAggregate(rs.getBigDecimal("rating_sum"), rs.getLong("rating_count"));

    public record CommunityStats(
            long ratingCount,
            BigDecimal ratingSum,
            BigDecimal averageRating,
            long likeCount,
            long listCount,
            long completedCount
    ) {
    }

    public record CanonicalStats(
            long ratingCount,
            BigDecimal ratingSum,
            long likeCount,
            long listCount,
            long completedCount
    ) {
    }

    public record CommunityAggregate(BigDecimal ratingSum, long ratingCount) {
    }

    public record RatingBucket(BigDecimal rating, long ratingCount) {
    }
}
