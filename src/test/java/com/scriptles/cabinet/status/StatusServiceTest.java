package com.scriptles.cabinet.status;

import com.scriptles.cabinet.media.repository.CatalogOutboxRepository;
import com.scriptles.cabinet.catalog.repository.CatalogJobRepository;
import com.scriptles.cabinet.user.importer.LetterboxdImportJobRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StatusServiceTest {
    @Test
    void returnsProductSummaryAndDoesNotExposeStoredFailureDetails() {
        BackgroundJobRunRepository runs = mock(BackgroundJobRunRepository.class);
        CatalogOutboxRepository outbox = mock(CatalogOutboxRepository.class);
        CatalogJobRepository catalogJobs = mock(CatalogJobRepository.class);
        LetterboxdImportJobRepository imports = mock(LetterboxdImportJobRepository.class);
        BackgroundJobRun failedTmdbRun = run(JobKey.TMDB_CATALOG_SYNC, JobRunStatus.FAILED,
                "secret provider payload and IllegalStateException stack");
        when(runs.findFirstByJobKeyOrderByStartedAtDesc(any())).thenAnswer(invocation -> {
            JobKey key = invocation.getArgument(0);
            if (key == JobKey.TMDB_CATALOG_SYNC) return Optional.of(failedTmdbRun);
            if (key == JobKey.COLLECTION_SYNC) return Optional.empty();
            return Optional.of(run(key, JobRunStatus.COMPLETED, null));
        });
        when(runs.countRecentFailures(any(), any())).thenReturn(0L);
        when(runs.findAllByFinishedAtIsNotNullOrderByFinishedAtDesc(any())).thenReturn(List.of(failedTmdbRun));
        when(outbox.countByStatus()).thenReturn(List.of());
        when(catalogJobs.countByStatus("PENDING")).thenReturn(2L);
        when(imports.countByStateIn(anyList())).thenReturn(0L);
        when(imports.findTop10ByStateInAndCompletedAtAfterOrderByCompletedAtDesc(anyList(), any()))
                .thenReturn(List.of());

        StatusService service = new StatusService(runs, outbox, catalogJobs, imports);
        ReflectionTestUtils.setField(service, "tmdbCron", "0 30 3 * * *");
        ReflectionTestUtils.setField(service, "tmdbZone", "America/Sao_Paulo");
        ReflectionTestUtils.setField(service, "collectionsCron", "0 0 5 * * *");
        ReflectionTestUtils.setField(service, "collectionsZone", "America/Sao_Paulo");
        ReflectionTestUtils.setField(service, "seriesCron", "0 0 4 * * *");
        ReflectionTestUtils.setField(service, "seriesZone", "America/Sao_Paulo");
        ReflectionTestUtils.setField(service, "episodeNotificationsCron", "0 0 8 * * *");
        ReflectionTestUtils.setField(service, "notificationRetentionCron", "0 20 3 * * *");
        ReflectionTestUtils.setField(service, "letterboxdCleanupCron", "0 17 * * * *");

        StatusResponse response = service.getStatus();
        String publicPayload = response.toString();

        assertThat(response.systems()).hasSize(4);
        assertThat(response.synchronizations()).hasSize(3);
        assertThat(response.synchronizations().stream()
                .filter(sync -> sync.key().equals("COLLECTIONS")).findFirst().orElseThrow().status())
                .isEqualTo(HealthStatus.OPERATIONAL);
        assertThat(response.status()).isNotNull();
        assertThat(response.backgroundProcessing().waiting()).isEqualTo(2);
        assertThat(response.scheduledJobs()).extracting(StatusResponse.ScheduledJobStatus::key)
                .contains("TMDB_CATALOG_SYNC", "COLLECTION_SYNC", "SERIES_TRACKING_SCAN");
        assertThat(response.scheduledJobs()).allSatisfy(job ->
                assertThat(job.schedule().frequency()).doesNotContain("0 30 3"));
        assertThat(publicPayload).doesNotContain("secret provider payload", "IllegalStateException", "stack");
        assertThat(response.recentActivity().getFirst().description())
                .isEqualTo("The scheduled operation could not be completed.");
    }

    private BackgroundJobRun run(JobKey key, JobRunStatus status, String summary) {
        BackgroundJobRun run = new BackgroundJobRun();
        Instant now = Instant.now();
        run.setJobKey(key);
        run.setStatus(status);
        run.setStartedAt(now.minusSeconds(2));
        run.setFinishedAt(now);
        run.setSummary(summary);
        return run;
    }
}
