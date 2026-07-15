package com.scriptles.cabinet.media.repository;

import com.scriptles.cabinet.media.entity.SeriesEpisode;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface SeriesEpisodeRepository extends JpaRepository<SeriesEpisode, UUID> {
}
