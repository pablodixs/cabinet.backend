package com.scriptles.cabinet.user.repository;

import com.scriptles.cabinet.user.entity.UserMedia;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.user.enums.UserMediaStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface UserMediaRepository extends JpaRepository<UserMedia, UUID> {
    Optional<UserMedia> findByUserIdAndMediaId(
            UUID userId,
            UUID mediaId
    );

    @EntityGraph(attributePaths = "media")
    List<UserMedia> findAllByUserId(UUID userId);

    Page<UserMedia> findByUserId(
            UUID userId,
            Pageable pageable
    );

    Page<UserMedia> findByUserIdAndStatus(
            UUID userId,
            UserMediaStatus status,
            Pageable pageable
    );

    long countByMediaIdAndStatusAndPrivateEntryFalse(
            UUID mediaId,
            UserMediaStatus status
    );

    @EntityGraph(attributePaths = "user")
    List<UserMedia> findTop3ByMediaIdAndStatusAndPrivateEntryFalseAndCompletedAtIsNotNullOrderByCompletedAtDescIdDesc(
            UUID mediaId,
            UserMediaStatus status
    );

    @EntityGraph(attributePaths = "user")
    @Query("""
            select entry from UserMedia entry
            where entry.media.id = :mediaId
              and entry.status = :status
              and entry.privateEntry = false
              and entry.completedAt is not null
              and not exists (select block.id from UserBlock block
                  where (block.blocker.id = :viewerId and block.blocked.id = entry.user.id)
                     or (block.blocker.id = entry.user.id and block.blocked.id = :viewerId))
            order by entry.completedAt desc, entry.id desc
            """)
    List<UserMedia> findRecentVisibleCompleters(
            @Param("mediaId") UUID mediaId,
            @Param("status") UserMediaStatus status,
            @Param("viewerId") UUID viewerId,
            Pageable pageable);

    @Query(
            value = """
                    select userMedia
                    from UserMedia userMedia
                    join fetch userMedia.media media
                    where userMedia.user.id = :userId
                      and (:status is null or userMedia.status = :status)
                      and (:type is null or media.typeValue = :#{#type == null ? null : #type.name()})
                    """,
            countQuery = """
                    select count(userMedia)
                    from UserMedia userMedia
                    join userMedia.media media
                    where userMedia.user.id = :userId
                      and (:status is null or userMedia.status = :status)
                      and (:type is null or media.typeValue = :#{#type == null ? null : #type.name()})
                    """
    )
    Page<UserMedia> findLibrary(
            @Param("userId") UUID userId,
            @Param("status") UserMediaStatus status,
            @Param("type") MediaType type,
            Pageable pageable
    );

    @Query("""
            select userMedia
            from UserMedia userMedia
            join fetch userMedia.media media
            where userMedia.user.id = :userId
              and (:includePrivate = true or userMedia.privateEntry = false)
            """)
    List<UserMedia> findRecentProfileLibrary(
            @Param("userId") UUID userId,
            @Param("includePrivate") boolean includePrivate,
            Pageable pageable
    );

    @Query(
            value = """
                    select userMedia
                    from UserMedia userMedia
                    join fetch userMedia.media media
                    where userMedia.user.id = :userId
                      and (:includePrivate = true or userMedia.privateEntry = false)
                    order by coalesce(
                        userMedia.lastInteractionAt,
                        userMedia.updatedAt,
                        userMedia.createdAt
                    ) desc,
                    userMedia.id desc
                    """,
            countQuery = """
                    select count(userMedia)
                    from UserMedia userMedia
                    where userMedia.user.id = :userId
                      and (:includePrivate = true or userMedia.privateEntry = false)
                    """
    )
    Page<UserMedia> findProfileActivities(
            @Param("userId") UUID userId,
            @Param("includePrivate") boolean includePrivate,
            Pageable pageable
    );

    @Query(value = """
            with media_stats as (
                select
                    count(*) as library_count,
                    coalesce(sum(case when um.status = 'COMPLETED' then 1 else 0 end), 0)
                        as completed_count,
                    coalesce(sum(case when um.status = 'IN_PROGRESS' then 1 else 0 end), 0)
                        as in_progress_count,
                    coalesce(sum(case
                        when um.status = 'COMPLETED' and m.type = 'ALBUM' then 1 else 0
                    end), 0) as albums_consumed,
                    coalesce(sum(case
                        when um.status = 'COMPLETED' and m.type = 'MOVIE' then 1 else 0
                    end), 0) as movies_consumed,
                    coalesce(sum(case
                        when um.status = 'COMPLETED' and m.type = 'BOOK' then 1 else 0
                    end), 0) as books_consumed,
                    coalesce(sum(case
                        when um.status = 'COMPLETED' and m.type = 'MOVIE'
                            then coalesce(movie.runtime_minutes, 0)
                        else 0
                    end), 0) as movie_minutes,
                    coalesce(sum(case
                        when um.status = 'COMPLETED' and m.type = 'BOOK'
                            then coalesce(book.page_count, 0)
                        else 0
                    end), 0) as pages_read
                from user_media um
                join media m on m.id = um.media_id
                left join movie_details movie on movie.media_id = um.media_id
                left join book_details book on book.media_id = um.media_id
                where um.user_id = :userId
                  and (:includePrivate = true or um.private_entry = false)
            ),
            episode_stats as (
                select
                    count(*) as episodes_watched,
                    count(distinct season.series_media_id) as series_consumed,
                    coalesce(sum(coalesce(episode.runtime_minutes, 0)), 0) as series_minutes
                from user_episode_watches watch
                join series_episodes episode on episode.id = watch.series_episode_id
                join series_seasons season on season.id = episode.season_id
                join user_media series_entry
                  on series_entry.user_id = watch.user_id
                 and series_entry.media_id = season.series_media_id
                where watch.user_id = :userId
                  and (:includePrivate = true or series_entry.private_entry = false)
            )
            select
                media.library_count,
                media.completed_count,
                media.in_progress_count,
                media.albums_consumed,
                media.movies_consumed,
                episode.series_consumed,
                media.books_consumed,
                episode.episodes_watched,
                media.pages_read,
                media.movie_minutes + episode.series_minutes as watched_minutes
            from media_stats media
            cross join episode_stats episode
            """, nativeQuery = true)
    ProfileStatisticsProjection findProfileStatistics(
            @Param("userId") UUID userId,
            @Param("includePrivate") boolean includePrivate
    );

    boolean existsByUserIdAndMediaId(
            UUID userId,
            UUID mediaId
    );

    @Query("""
            select userMedia.media.id as mediaId, count(userMedia.id) as activityCount
            from UserMedia userMedia
            where userMedia.privateEntry = false
              and userMedia.media.typeValue in :types
              and coalesce(userMedia.lastInteractionAt, userMedia.updatedAt, userMedia.createdAt) >= :since
            group by userMedia.media.id
            order by count(userMedia.id) desc, userMedia.media.id asc
            """)
    List<MediaActivityProjection> findRecentPublicActivity(@Param("types") Set<String> types,
                                                            @Param("since") Instant since,
                                                            Pageable pageable);

    @Query("""
            select media as media, count(userMedia.id) as plannedCount
            from UserMedia userMedia
            join userMedia.media media
            where userMedia.status = :status
              and userMedia.privateEntry = false
              and media.typeValue = 'MOVIE'
              and media.releaseDate > :today
            group by media
            order by count(userMedia.id) desc,
                     media.releaseDate asc,
                     lower(media.title) asc,
                     media.id asc
            """)
    List<AnticipatedMediaProjection> findMostAnticipatedMovies(
            @Param("status") UserMediaStatus status,
            @Param("today") LocalDate today,
            Pageable pageable
    );

    interface MediaActivityProjection {
        UUID getMediaId();
        long getActivityCount();
    }

    interface AnticipatedMediaProjection {
        Media getMedia();
        long getPlannedCount();
    }

    interface ProfileStatisticsProjection {
        long getLibraryCount();
        long getCompletedCount();
        long getInProgressCount();
        long getWatchedMinutes();
        long getPagesRead();
        long getEpisodesWatched();
        long getAlbumsConsumed();
        long getMoviesConsumed();
        long getSeriesConsumed();
        long getBooksConsumed();
    }

    @Query("""
            select distinct entry.media.id
            from UserMedia entry
            where entry.status = :status
              and entry.media.typeValue = 'SERIES'
            """)
    List<UUID> findDistinctSeriesIdsByStatus(@Param("status") UserMediaStatus status);

    @Query("""
            select entry.media.id
            from UserMedia entry
            where entry.user.id = :userId
              and entry.status = :status
              and entry.media.typeValue = 'SERIES'
            """)
    List<UUID> findSeriesIdsForUserAndStatus(@Param("userId") UUID userId,
                                             @Param("status") UserMediaStatus status);

    List<UserMedia> findAllByMediaIdAndStatus(UUID mediaId, UserMediaStatus status);
}
