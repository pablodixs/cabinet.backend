package com.scriptles.cabinet.media.repository;

import com.scriptles.cabinet.media.entity.AlbumTrack;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

public interface AlbumTrackRepository extends JpaRepository<AlbumTrack, UUID> {
    List<AlbumTrack> findAllByAlbumIdOrderByDiscNumberAscTrackNumberAsc(UUID albumId);
    boolean existsByTrackMediaId(UUID trackMediaId);
    Optional<AlbumTrack> findByAlbumIdAndDiscNumberAndTrackNumber(
            UUID albumId,
            Integer discNumber,
            Integer trackNumber
    );
}
