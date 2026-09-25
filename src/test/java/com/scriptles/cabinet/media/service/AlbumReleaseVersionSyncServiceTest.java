package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.external.ExternalMediaRateLimitException;
import com.scriptles.cabinet.media.external.MusicBrainzClient;
import com.scriptles.cabinet.media.repository.ExternalReferenceRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlbumReleaseVersionSyncServiceTest {
    private static final UUID ALBUM_ID = UUID.randomUUID();
    private static final String RELEASE_GROUP_ID = "musicbrainz-release-group";

    @Mock
    private MusicBrainzClient musicBrainzClient;
    @Mock
    private ExternalReferenceRepository externalReferenceRepository;
    @Mock
    private AlbumReleaseVersionPersistenceService persistenceService;

    private SimpleMeterRegistry meters;
    private AlbumReleaseVersionSyncService service;

    @BeforeEach
    void setUp() {
        meters = new SimpleMeterRegistry();
        service = new AlbumReleaseVersionSyncService(
                musicBrainzClient, externalReferenceRepository, persistenceService, meters);
        ExternalReference reference = new ExternalReference();
        reference.setExternalId(RELEASE_GROUP_ID);
        when(externalReferenceRepository.findByMediaIdAndSource(ALBUM_ID, ExternalSource.MUSICBRAINZ))
                .thenReturn(Optional.of(reference));
    }

    @Test
    void recordsSuccessfulReleaseVersionSyncMetrics() {
        when(musicBrainzClient.findAlbumReleaseVersions(RELEASE_GROUP_ID)).thenReturn(List.of());

        service.synchronize(ALBUM_ID, RELEASE_GROUP_ID);

        verify(persistenceService).upsert(ALBUM_ID, List.of());
        assertThat(meters.get("cabinet.provider.requests").tag("provider", "MUSICBRAINZ")
                .tag("operation", "release_versions_sync").counter().count()).isEqualTo(1);
        assertThat(meters.get("cabinet.provider.duration").tag("provider", "MUSICBRAINZ")
                .tag("operation", "release_versions_sync").tag("outcome", "success").timer().count())
                .isEqualTo(1);
    }

    @Test
    void recordsFailedAndRateLimitedSyncMetricsBeforeRetryingTheOutboxEvent() {
        when(musicBrainzClient.findAlbumReleaseVersions(RELEASE_GROUP_ID))
                .thenThrow(new ExternalMediaRateLimitException("rate limited", null));

        assertThatThrownBy(() -> service.synchronize(ALBUM_ID, RELEASE_GROUP_ID))
                .isInstanceOf(ExternalMediaRateLimitException.class);

        assertThat(meters.get("cabinet.provider.failures").tag("provider", "MUSICBRAINZ")
                .tag("operation", "release_versions_sync").counter().count()).isEqualTo(1);
        assertThat(meters.get("cabinet.provider.rate_limited").tag("provider", "MUSICBRAINZ")
                .tag("operation", "release_versions_sync").counter().count()).isEqualTo(1);
        assertThat(meters.get("cabinet.provider.duration").tag("provider", "MUSICBRAINZ")
                .tag("operation", "release_versions_sync").tag("outcome", "failure").timer().count())
                .isEqualTo(1);
    }
}
