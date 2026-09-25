package com.scriptles.cabinet.media.repository;

import com.scriptles.cabinet.media.entity.AlbumTrack;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import java.util.Collection;

public interface AlbumTrackRepository extends JpaRepository<AlbumTrack, UUID> {
    @EntityGraph(attributePaths = "album")
    List<AlbumTrack> findAllByTrackMediaIdIn(Collection<UUID> trackMediaIds);
    List<AlbumTrack> findAllByAlbumIdOrderByDiscNumberAscTrackNumberAsc(UUID albumId);

    @Query("""
            select track from AlbumTrack track
            where track.album.id = :albumId
              and (
                :firstPage = true
                or (
                    :cursorDiscNull = true and track.discNumber is null and (
                        (:cursorTrackNull = false and (track.trackNumber is null or track.trackNumber > :cursorTrack))
                        or (:cursorTrackNull = true and track.trackNumber is null and track.id > :cursorId)
                        or (track.trackNumber = :cursorTrack and track.id > :cursorId)
                    )
                )
                or (
                    :cursorDiscNull = false and (
                        track.discNumber is null
                        or track.discNumber > :cursorDisc
                        or (track.discNumber = :cursorDisc and (
                            (:cursorTrackNull = false and (track.trackNumber is null or track.trackNumber > :cursorTrack))
                            or (:cursorTrackNull = true and track.trackNumber is null and track.id > :cursorId)
                            or (track.trackNumber = :cursorTrack and track.id > :cursorId)
                        ))
                    )
                )
              )
            order by track.discNumber asc nulls last, track.trackNumber asc nulls last, track.id asc
            """)
    List<AlbumTrack> findAlbumPageAfter(
            @Param("albumId") UUID albumId,
            @Param("firstPage") boolean firstPage,
            @Param("cursorDiscNull") boolean cursorDiscNull,
            @Param("cursorDisc") Integer cursorDisc,
            @Param("cursorTrackNull") boolean cursorTrackNull,
            @Param("cursorTrack") Integer cursorTrack,
            @Param("cursorId") UUID cursorId,
            Pageable pageable
    );
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
