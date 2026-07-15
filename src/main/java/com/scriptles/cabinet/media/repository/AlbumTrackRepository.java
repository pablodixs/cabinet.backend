package com.scriptles.cabinet.media.repository;

import com.scriptles.cabinet.media.entity.AlbumTrack;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface AlbumTrackRepository extends JpaRepository<AlbumTrack, UUID> {
    List<AlbumTrack> findAllByAlbumIdOrderByDiscNumberAscTrackNumberAsc(UUID albumId);
}
