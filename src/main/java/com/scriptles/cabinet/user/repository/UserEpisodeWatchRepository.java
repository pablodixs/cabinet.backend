package com.scriptles.cabinet.user.repository;

import com.scriptles.cabinet.media.entity.SeriesEpisode;
import com.scriptles.cabinet.user.entity.UserEpisodeWatch;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface UserEpisodeWatchRepository extends JpaRepository<UserEpisodeWatch, UUID> {
    Optional<UserEpisodeWatch> findByUserIdAndEpisodeId(UUID userId, UUID episodeId);

    boolean existsByUserIdAndEpisodeId(UUID userId, UUID episodeId);

    @Query("""
            select watch.episode.id
            from UserEpisodeWatch watch
            where watch.user.id = :userId and watch.episode.id in :episodeIds
            """)
    Set<UUID> findWatchedEpisodeIds(@Param("userId") UUID userId,
                                    @Param("episodeIds") Collection<UUID> episodeIds);

    @Query("""
            select count(episode)
            from SeriesEpisode episode
            where episode.season.series.id = :seriesId
              and episode.season.seasonNumber > 0
              and (episode.season.seasonNumber < :seasonNumber
                   or (episode.season.seasonNumber = :seasonNumber
                       and episode.episodeNumber < :episodeNumber))
              and (episode.airDate is null or episode.airDate <= :today)
              and not exists (
                  select watch.id from UserEpisodeWatch watch
                  where watch.user.id = :userId and watch.episode = episode
              )
            """)
    long countPreviousUnwatched(@Param("userId") UUID userId,
                                @Param("seriesId") UUID seriesId,
                                @Param("seasonNumber") int seasonNumber,
                                @Param("episodeNumber") int episodeNumber,
                                @Param("today") LocalDate today);

    @Query("""
            select episode
            from SeriesEpisode episode
            where episode.season.series.id = :seriesId
              and episode.season.seasonNumber > 0
              and (episode.season.seasonNumber < :seasonNumber
                   or (episode.season.seasonNumber = :seasonNumber
                       and episode.episodeNumber < :episodeNumber))
              and (episode.airDate is null or episode.airDate <= :today)
              and not exists (
                  select watch.id from UserEpisodeWatch watch
                  where watch.user.id = :userId and watch.episode = episode
              )
            order by episode.season.seasonNumber, episode.episodeNumber
            """)
    List<SeriesEpisode> findPreviousUnwatched(@Param("userId") UUID userId,
                                              @Param("seriesId") UUID seriesId,
                                              @Param("seasonNumber") int seasonNumber,
                                              @Param("episodeNumber") int episodeNumber,
                                              @Param("today") LocalDate today);

    @Query("""
            select episode
            from SeriesEpisode episode
            join fetch episode.season season
            join fetch season.series series
            join fetch episode.episodeMedia episodeMedia
            where episode.season.seasonNumber > 0
              and episode.airDate < :today
              and exists (
                  select entry.id from UserMedia entry
                  where entry.user.id = :userId
                    and entry.media = episode.season.series
                    and entry.status = com.scriptles.cabinet.user.enums.UserMediaStatus.IN_PROGRESS
              )
              and not exists (
                  select watch.id from UserEpisodeWatch watch
                  where watch.user.id = :userId and watch.episode = episode
              )
            order by episode.airDate, episode.season.series.title,
                     episode.season.seasonNumber, episode.episodeNumber
            """)
    List<SeriesEpisode> findOverdue(@Param("userId") UUID userId,
                                    @Param("today") LocalDate today,
                                    Pageable pageable);

    @Query("""
            select count(episode)
            from SeriesEpisode episode
            where episode.season.seasonNumber > 0
              and episode.airDate < :today
              and exists (
                  select entry.id from UserMedia entry
                  where entry.user.id = :userId
                    and entry.media = episode.season.series
                    and entry.status = com.scriptles.cabinet.user.enums.UserMediaStatus.IN_PROGRESS
              )
              and not exists (
                  select watch.id from UserEpisodeWatch watch
                  where watch.user.id = :userId and watch.episode = episode
              )
            """)
    long countOverdue(@Param("userId") UUID userId, @Param("today") LocalDate today);

    @Query("""
            select episode
            from SeriesEpisode episode
            join fetch episode.season season
            join fetch season.series series
            join fetch episode.episodeMedia episodeMedia
            where episode.season.seasonNumber > 0
              and episode.airDate >= :fromDate
              and episode.airDate <= :toDate
              and exists (
                  select entry.id from UserMedia entry
                  where entry.user.id = :userId
                    and entry.media = episode.season.series
                    and entry.status = com.scriptles.cabinet.user.enums.UserMediaStatus.IN_PROGRESS
              )
            order by episode.airDate, episode.season.series.title,
                     episode.season.seasonNumber, episode.episodeNumber
            """)
    List<SeriesEpisode> findUpcoming(@Param("userId") UUID userId,
                                     @Param("fromDate") LocalDate fromDate,
                                     @Param("toDate") LocalDate toDate);

    @Query("""
            select count(watch)
            from UserEpisodeWatch watch
            where watch.user.id = :userId
              and watch.episode.season.series.id = :seriesId
              and watch.episode.season.seasonNumber > 0
            """)
    long countRegularWatched(@Param("userId") UUID userId, @Param("seriesId") UUID seriesId);
}
