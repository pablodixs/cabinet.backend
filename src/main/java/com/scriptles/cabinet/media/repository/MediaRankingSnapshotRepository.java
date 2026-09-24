package com.scriptles.cabinet.media.repository;

import com.scriptles.cabinet.media.service.TrendingScoreCalculator;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public class MediaRankingSnapshotRepository {
    private static final String RECENT_ACTIVITY_SQL = """
            select snapshot.media_id
            from media_ranking_snapshot snapshot
            join media on media.id = snapshot.media_id
            where media.type in (:types)
              and snapshot.activity_day >=
                    ((current_timestamp at time zone 'UTC')::date - (:periodDays - 1))
            group by snapshot.media_id, media.title
            order by %s desc, lower(media.title), snapshot.media_id
            limit :limit
            """;

    private static final String REBUILD_SQL = """
            with activity as (
                select rating.media_id,
                       coalesce(rating.updated_at, rating.rated_at, rating.created_at) as occurred_at,
                       1::bigint as rating_activity,
                       0::bigint as like_activity,
                       0::bigint as completion_activity,
                       0::bigint as log_activity,
                       0::bigint as list_addition_activity,
                       0::bigint as review_activity
                from ratings rating
                where rating.media_id = :mediaId
                  and rating.visibility = 'PUBLIC'

                union all

                select media_like.media_id,
                       coalesce(media_like.liked_at, media_like.created_at),
                       0::bigint, 1::bigint, 0::bigint, 0::bigint, 0::bigint, 0::bigint
                from media_likes media_like
                where media_like.media_id = :mediaId

                union all

                select user_media.media_id,
                       coalesce(user_media.completed_at, user_media.last_interaction_at,
                                user_media.updated_at, user_media.created_at),
                       0::bigint, 0::bigint, 1::bigint, 0::bigint, 0::bigint, 0::bigint
                from user_media
                where user_media.media_id = :mediaId
                  and user_media.status = 'COMPLETED'
                  and user_media.private_entry = false

                union all

                select activity.media_id,
                       coalesce(activity.created_at,
                                activity.occurred_on::timestamp at time zone 'America/Sao_Paulo'),
                       0::bigint, 0::bigint, 0::bigint, 1::bigint, 0::bigint, 0::bigint
                from user_media_activities activity
                where activity.media_id = :mediaId
                  and activity.visibility = 'PUBLIC'
                  and activity.type in ('LOGGED', 'RELOGGED', 'WATCHED', 'REWATCHED', 'MARKED_WATCHED')

                union all

                select item.media_id,
                       coalesce(item.created_at, current_timestamp),
                       0::bigint, 0::bigint, 0::bigint, 0::bigint, 1::bigint, 0::bigint
                from media_list_items item
                join media_lists list on list.id = item.list_id
                where item.media_id = :mediaId
                  and list.visibility = 'PUBLIC'

                union all

                select review.media_id,
                       coalesce(review.updated_at, review.published_at, review.created_at),
                       0::bigint, 0::bigint, 0::bigint, 0::bigint, 0::bigint, 1::bigint
                from reviews review
                where review.media_id = :mediaId
                  and review.visibility = 'PUBLIC'
            )
            insert into media_ranking_snapshot (
                media_id, activity_day, rating_activity, like_activity, completion_activity,
                log_activity, list_addition_activity, review_activity, updated_at
            )
            select :mediaId,
                   (activity.occurred_at at time zone 'UTC')::date,
                   sum(activity.rating_activity),
                   sum(activity.like_activity),
                   sum(activity.completion_activity),
                   sum(activity.log_activity),
                   sum(activity.list_addition_activity),
                   sum(activity.review_activity),
                   current_timestamp
            from activity
            where activity.occurred_at >= current_timestamp - interval '30 days'
              and activity.occurred_at <= current_timestamp
            group by (activity.occurred_at at time zone 'UTC')::date
            on conflict (media_id, activity_day) do update set
                rating_activity = excluded.rating_activity,
                like_activity = excluded.like_activity,
                completion_activity = excluded.completion_activity,
                log_activity = excluded.log_activity,
                list_addition_activity = excluded.list_addition_activity,
                review_activity = excluded.review_activity,
                updated_at = excluded.updated_at
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final TrendingScoreCalculator scoreCalculator;

    public MediaRankingSnapshotRepository(
            NamedParameterJdbcTemplate jdbc,
            TrendingScoreCalculator scoreCalculator
    ) {
        this.jdbc = jdbc;
        this.scoreCalculator = scoreCalculator;
    }

    public List<UUID> findTrendingMediaIds(Collection<String> types, int periodDays, int limit) {
        MapSqlParameterSource parameters = scoreCalculator.parameters(periodDays)
                .addValue("types", types)
                .addValue("limit", limit);
        return jdbc.query(
                RECENT_ACTIVITY_SQL.formatted(scoreCalculator.sqlScoreExpression()),
                parameters,
                (rs, rowNum) -> rs.getObject("media_id", UUID.class));
    }

    public void rebuild(UUID mediaId) {
        var parameters = new MapSqlParameterSource("mediaId", mediaId);
        long dirtyVersion = jdbc.query("""
                select dirty_version
                from media_ranking_snapshot_state
                where media_id = :mediaId
                for update
                """, parameters, (rs, rowNum) -> rs.getLong("dirty_version"))
                .stream().findFirst().orElse(0L);
        jdbc.update("delete from media_ranking_snapshot where media_id = :mediaId", parameters);
        jdbc.update(REBUILD_SQL, parameters);
        jdbc.update("""
                insert into media_ranking_snapshot_state (
                    media_id, rebuilt_at, dirty, dirty_version, dirty_marked_at
                ) values (:mediaId, null, false, 0, current_timestamp)
                on conflict (media_id) do nothing
                """, parameters);
        parameters.addValue("dirtyVersion", dirtyVersion);
        jdbc.update("""
                update media_ranking_snapshot_state
                set rebuilt_at = current_timestamp,
                    dirty = false,
                    dirty_marked_at = current_timestamp
                where media_id = :mediaId and dirty_version = :dirtyVersion
                """, parameters);
    }

    public void markDirty(UUID mediaId) {
        jdbc.update("""
                insert into media_ranking_snapshot_state (
                    media_id, rebuilt_at, dirty, dirty_version, dirty_marked_at
                ) values (:mediaId, null, true, 1, current_timestamp)
                on conflict (media_id) do update set
                    dirty = true,
                    dirty_version = media_ranking_snapshot_state.dirty_version + 1,
                    dirty_marked_at = current_timestamp
                """, new MapSqlParameterSource("mediaId", mediaId));
    }

    public List<UUID> findDirtyMediaIds(int limit) {
        return findMediaIds("""
                select media_id from media_ranking_snapshot_state
                where dirty = true
                order by dirty_marked_at, media_id
                limit :limit
                """, limit);
    }

    @Transactional
    public List<UUID> claimBackfillBatch(int limit) {
        List<BackfillCursor> cursors = jdbc.query("""
                select last_media_id, completed_at
                from media_ranking_snapshot_backfill_state
                where singleton_id = 1
                for update
                """, new MapSqlParameterSource(), (rs, rowNum) -> new BackfillCursor(
                rs.getObject("last_media_id", UUID.class),
                rs.getTimestamp("completed_at")));
        if (cursors.isEmpty()) {
            throw new IllegalStateException("Ranking snapshot backfill cursor is missing");
        }
        BackfillCursor cursor = cursors.getFirst();
        if (cursor.completedAt() != null) return List.of();

        List<UUID> mediaIds = jdbc.query("""
                select media.id as media_id
                from media
                left join media_ranking_snapshot_state state on state.media_id = media.id
                where state.media_id is null
                  and (cast(:lastMediaId as uuid) is null
                       or media.id > cast(:lastMediaId as uuid))
                order by media.id
                limit :limit
                """, new MapSqlParameterSource()
                .addValue("lastMediaId", cursor.lastMediaId())
                .addValue("limit", limit),
                (rs, rowNum) -> rs.getObject("media_id", UUID.class));
        if (mediaIds.isEmpty()) {
            jdbc.update("""
                    update media_ranking_snapshot_backfill_state
                    set completed_at = current_timestamp
                    where singleton_id = 1
                    """, new MapSqlParameterSource());
            return List.of();
        }

        mediaIds.forEach(this::markDirty);
        jdbc.update("""
                update media_ranking_snapshot_backfill_state
                set last_media_id = :lastMediaId
                where singleton_id = 1
                """, new MapSqlParameterSource("lastMediaId", mediaIds.getLast()));
        return mediaIds;
    }

    public List<UUID> findStaleMediaIds(Instant updatedBefore, int limit) {
        return jdbc.query("""
                select media_id
                from media_ranking_snapshot_state
                where dirty = false and rebuilt_at < :updatedBefore
                  and exists (
                      select 1
                      from media_ranking_snapshot snapshot
                      where snapshot.media_id = media_ranking_snapshot_state.media_id
                        and snapshot.activity_day >=
                            ((current_timestamp at time zone 'UTC')::date - 29)
                  )
                order by rebuilt_at, media_id
                limit :limit
                """, new MapSqlParameterSource()
                .addValue("updatedBefore", Timestamp.from(updatedBefore))
                .addValue("limit", limit),
                (rs, rowNum) -> rs.getObject("media_id", UUID.class));
    }

    private List<UUID> findMediaIds(String sql, int limit) {
        return jdbc.query(sql, new MapSqlParameterSource("limit", limit),
                (rs, rowNum) -> rs.getObject("media_id", UUID.class));
    }

    private record BackfillCursor(UUID lastMediaId, Timestamp completedAt) {
    }
}
