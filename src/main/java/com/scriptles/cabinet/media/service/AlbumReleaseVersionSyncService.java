package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.external.ExternalMediaRateLimitException;
import com.scriptles.cabinet.media.external.MusicBrainzClient;
import com.scriptles.cabinet.media.repository.ExternalReferenceRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AlbumReleaseVersionSyncService {
    private final MusicBrainzClient musicBrainzClient;
    private final ExternalReferenceRepository externalReferenceRepository;
    private final AlbumReleaseVersionPersistenceService persistenceService;
    private final MeterRegistry meters;

    public void synchronize(UUID albumMediaId, String releaseGroupId) {
        ExternalReference reference = externalReferenceRepository
                .findByMediaIdAndSource(albumMediaId, ExternalSource.MUSICBRAINZ)
                .orElseThrow(() -> new IllegalStateException(
                        "Canonical album has no MusicBrainz Release Group reference"));
        if (!releaseGroupId.equals(reference.getExternalId())) {
            throw new IllegalStateException("Release Group does not match the canonical album reference");
        }

        Timer.Sample sample = Timer.start(meters);
        String outcome = "success";
        meters.counter("cabinet.provider.requests", "provider", "MUSICBRAINZ",
                "operation", "release_versions_sync").increment();
        List<MusicBrainzClient.AlbumReleaseVersionSnapshot> versions;
        try {
            versions = musicBrainzClient.findAlbumReleaseVersions(releaseGroupId);
        } catch (RuntimeException failure) {
            outcome = "failure";
            meters.counter("cabinet.provider.failures", "provider", "MUSICBRAINZ",
                    "operation", "release_versions_sync").increment();
            if (failure instanceof ExternalMediaRateLimitException) {
                meters.counter("cabinet.provider.rate_limited", "provider", "MUSICBRAINZ",
                        "operation", "release_versions_sync").increment();
            }
            throw failure;
        } finally {
            sample.stop(meters.timer("cabinet.provider.duration", "provider", "MUSICBRAINZ",
                    "operation", "release_versions_sync", "outcome", outcome));
        }
        persistenceService.upsert(albumMediaId, versions);
    }
}
