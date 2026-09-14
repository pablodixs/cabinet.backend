package com.scriptles.cabinet.media.repository;

import com.scriptles.cabinet.media.entity.SeriesEpisode;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.List;
import java.util.Optional;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SeriesEpisodeRepository extends JpaRepository<SeriesEpisode, UUID> {
    @EntityGraph(attributePaths = {"season", "season.series"})
    List<SeriesEpisode> findAllByEpisodeMediaIdIn(java.util.Collection<UUID> episodeMediaIds);
    List<SeriesEpisode> findAllBySeasonIdOrderByEpisodeNumberAsc(UUID seasonId);
    Optional<SeriesEpisode> findBySeasonIdAndEpisodeNumber(UUID seasonId, Integer episodeNumber);
    Optional<SeriesEpisode> findByEpisodeMediaId(UUID mediaId);

    @Query("""
            select episode.episodeMedia.id
            from SeriesEpisode episode
            where episode.season.series.id = :seriesId
              and (episode.airDate is null or episode.airDate <= :today)
            """)
    List<UUID> findEligibleEpisodeMediaIdsBySeriesId(@Param("seriesId") UUID seriesId,
                                                      @Param("today") LocalDate today);

    @Query("""
            select episode.season.series.id
            from SeriesEpisode episode
            where episode.episodeMedia.id = :episodeMediaId
            """)
    Optional<UUID> findSeriesIdByEpisodeMediaId(@Param("episodeMediaId") UUID episodeMediaId);

    long countBySeasonSeriesIdAndSeasonSeasonNumberGreaterThan(UUID seriesId, Integer seasonNumber);

    @EntityGraph(attributePaths = {"season", "season.series", "episodeMedia"})
    List<SeriesEpisode> findAllByAirDateAndSeasonSeasonNumberGreaterThan(LocalDate airDate, Integer seasonNumber);
}
