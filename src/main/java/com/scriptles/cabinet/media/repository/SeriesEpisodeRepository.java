package com.scriptles.cabinet.media.repository;

import com.scriptles.cabinet.media.entity.SeriesEpisode;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.List;
import java.util.Optional;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.EntityGraph;

public interface SeriesEpisodeRepository extends JpaRepository<SeriesEpisode, UUID> {
    List<SeriesEpisode> findAllBySeasonIdOrderByEpisodeNumberAsc(UUID seasonId);
    Optional<SeriesEpisode> findBySeasonIdAndEpisodeNumber(UUID seasonId, Integer episodeNumber);
    Optional<SeriesEpisode> findByEpisodeMediaId(UUID mediaId);

    long countBySeasonSeriesIdAndSeasonSeasonNumberGreaterThan(UUID seriesId, Integer seasonNumber);

    @EntityGraph(attributePaths = {"season", "season.series", "episodeMedia"})
    List<SeriesEpisode> findAllByAirDateAndSeasonSeasonNumberGreaterThan(LocalDate airDate, Integer seasonNumber);
}
