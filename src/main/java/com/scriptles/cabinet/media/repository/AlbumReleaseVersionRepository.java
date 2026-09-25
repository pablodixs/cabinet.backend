package com.scriptles.cabinet.media.repository;

import com.scriptles.cabinet.media.entity.AlbumReleaseVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.Collection;
import java.util.UUID;

public interface AlbumReleaseVersionRepository extends JpaRepository<AlbumReleaseVersion, UUID> {
    @Query("select version from AlbumReleaseVersion version where version.album.id = :albumId " +
            "order by version.isPrimary desc, version.releaseDate asc nulls last")
    List<AlbumReleaseVersion> findAllForAlbum(UUID albumId);

    @Query("select version from AlbumReleaseVersion version " +
            "where version.album.id = :albumId and (:cursorId is null or version.id > :cursorId) " +
            "order by version.id asc")
    List<AlbumReleaseVersion> findPageForAlbumAfter(UUID albumId, UUID cursorId, Pageable pageable);

    Optional<AlbumReleaseVersion> findByMusicBrainzReleaseId(UUID musicBrainzReleaseId);

    List<AlbumReleaseVersion> findAllByMusicBrainzReleaseIdIn(Collection<UUID> musicBrainzReleaseIds);

    @Query("select version from AlbumReleaseVersion version where version.barcode = :barcode " +
            "order by version.isPrimary desc, version.releaseDate asc nulls last")
    List<AlbumReleaseVersion> findAllByBarcode(String barcode);

    @Modifying
    @Query("update AlbumReleaseVersion version set version.isPrimary = false where version.album.id = :albumId")
    int clearPrimaryForAlbum(UUID albumId);
}
