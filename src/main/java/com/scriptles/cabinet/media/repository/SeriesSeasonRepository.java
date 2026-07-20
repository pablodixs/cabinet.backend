package com.scriptles.cabinet.media.repository;

import com.scriptles.cabinet.media.entity.SeriesSeason;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SeriesSeasonRepository extends JpaRepository<SeriesSeason, UUID> {
    List<SeriesSeason> findAllBySeriesIdOrderBySeasonNumberAsc(UUID seriesId);
    List<SeriesSeason> findAllBySeriesIdInAndSeasonNumberGreaterThanOrderBySeriesIdAscSeasonNumberAsc(
            List<UUID> seriesIds, Integer seasonNumber);
    Optional<SeriesSeason> findBySeriesIdAndSeasonNumber(UUID seriesId, Integer seasonNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SeriesSeason s where s.series.id = :seriesId and s.seasonNumber = :seasonNumber")
    Optional<SeriesSeason> findLockedBySeriesIdAndSeasonNumber(@Param("seriesId") UUID seriesId,
                                                               @Param("seasonNumber") Integer seasonNumber);
}
