package com.scriptles.cabinet.media.repository;

import com.scriptles.cabinet.media.entity.SeriesSeason;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SeriesSeasonRepository extends JpaRepository<SeriesSeason, UUID> {
    List<SeriesSeason> findAllBySeriesIdOrderBySeasonNumberAsc(UUID seriesId);
}
