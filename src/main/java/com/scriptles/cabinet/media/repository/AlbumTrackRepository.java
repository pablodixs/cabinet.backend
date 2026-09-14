package com.scriptles.cabinet.media.repository;

import com.scriptles.cabinet.media.entity.AlbumTrack;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import java.util.Collection;

public interface AlbumTrackRepository extends JpaRepository<AlbumTrack, UUID> {
    @EntityGraph(attributePaths = "album")
    List<AlbumTrack> findAllByTrackMediaIdIn(Collection<UUID> trackMediaIds);
    List<AlbumTrack> findAllByAlbumIdOrderByDiscNumberAscTrackNumberAsc(UUID albumId);
    boolean existsByTrackMediaId(UUID trackMediaId);

    @Query("select distinct track.trackMedia.id from AlbumTrack track where track.album.id = :albumId")
    List<UUID> findTrackMediaIdsByAlbumId(@Param("albumId") UUID albumId);

    @Query("select distinct track.album.id from AlbumTrack track where track.trackMedia.id = :trackMediaId")
    List<UUID> findAlbumIdsByTrackMediaId(@Param("trackMediaId") UUID trackMediaId);

    Optional<AlbumTrack> findByAlbumIdAndDiscNumberAndTrackNumber(
            UUID albumId,
            Integer discNumber,
            Integer trackNumber
    );
}
