package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.media.entity.AlbumReleaseVersion;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.external.MusicBrainzClient;
import com.scriptles.cabinet.media.repository.AlbumReleaseVersionRepository;
import com.scriptles.cabinet.media.repository.MediaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AlbumReleaseVersionPersistenceService {
    private final AlbumReleaseVersionRepository versionRepository;
    private final MediaRepository mediaRepository;

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = "mediaDetails", key = "#albumMediaId + ':pt-BR'"),
            @CacheEvict(cacheNames = "mediaDetails", key = "#albumMediaId + ':en-US'")
    })
    public void upsert(UUID albumMediaId, List<MusicBrainzClient.AlbumReleaseVersionSnapshot> snapshots) {
        Media album = mediaRepository.findById(albumMediaId)
                .orElseThrow(() -> new IllegalStateException("Canonical album no longer exists"));
        if (album.getType() != MediaType.ALBUM) {
            throw new IllegalStateException("Release versions can only be attached to canonical albums");
        }
        if (snapshots.isEmpty()) {
            return;
        }

        List<AlbumReleaseVersion> matching = versionRepository.findAllByMusicBrainzReleaseIdIn(snapshots.stream()
                .map(MusicBrainzClient.AlbumReleaseVersionSnapshot::musicBrainzReleaseId)
                .distinct().toList());
        Map<UUID, AlbumReleaseVersion> byReleaseId = new HashMap<>();
        matching.forEach(version -> byReleaseId.put(version.getMusicBrainzReleaseId(), version));

        versionRepository.clearPrimaryForAlbum(albumMediaId);
        versionRepository.flush();

        Instant syncedAt = Instant.now();
        for (MusicBrainzClient.AlbumReleaseVersionSnapshot snapshot : snapshots) {
            AlbumReleaseVersion version = byReleaseId.getOrDefault(
                    snapshot.musicBrainzReleaseId(), new AlbumReleaseVersion());
            if (version.getId() != null && !version.getAlbum().getId().equals(albumMediaId)) {
                throw new IllegalStateException("MusicBrainz release is already linked to another canonical album");
            }
            version.setAlbum(album);
            version.setMusicBrainzReleaseId(snapshot.musicBrainzReleaseId());
            version.setTitle(snapshot.title());
            version.setCountryCode(snapshot.countryCode());
            version.setReleaseDate(snapshot.releaseDate());
            version.setFormat(snapshot.format());
            version.setStatus(snapshot.status());
            version.setBarcode(snapshot.barcode());
            version.setCatalogNumber(snapshot.catalogNumber());
            version.setLabelName(snapshot.labelName());
            version.setCoverUrl(snapshot.coverUrl());
            version.setTrackCount(snapshot.trackCount());
            version.setPrimary(snapshot.primary());
            version.setLastSyncedAt(syncedAt);
            versionRepository.save(version);
        }
    }
}
