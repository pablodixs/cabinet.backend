package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.external.MusicBrainzClient;
import com.scriptles.cabinet.media.repository.ExternalReferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AlbumReleaseVersionSyncService {
    private final MusicBrainzClient musicBrainzClient;
    private final ExternalReferenceRepository externalReferenceRepository;
    private final AlbumReleaseVersionPersistenceService persistenceService;

    public void synchronize(UUID albumMediaId, String releaseGroupId) {
        ExternalReference reference = externalReferenceRepository
                .findByMediaIdAndSource(albumMediaId, ExternalSource.MUSICBRAINZ)
                .orElseThrow(() -> new IllegalStateException(
                        "Canonical album has no MusicBrainz Release Group reference"));
        if (!releaseGroupId.equals(reference.getExternalId())) {
            throw new IllegalStateException("Release Group does not match the canonical album reference");
        }

        var versions = musicBrainzClient.findAlbumReleaseVersions(releaseGroupId);
        persistenceService.upsert(albumMediaId, versions);
    }
}
