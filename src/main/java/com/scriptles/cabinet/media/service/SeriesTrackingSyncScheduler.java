package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.media.entity.SeriesSeason;
import com.scriptles.cabinet.media.event.SeriesTrackingRequestedEvent;
import com.scriptles.cabinet.media.repository.SeriesSeasonRepository;
import com.scriptles.cabinet.media.repository.ExternalReferenceRepository;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.status.BackgroundJobRunner;
import com.scriptles.cabinet.status.BackgroundJobTracker;
import com.scriptles.cabinet.status.JobKey;
import com.scriptles.cabinet.user.enums.UserMediaStatus;
import com.scriptles.cabinet.user.repository.UserMediaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

@Service
@Slf4j
public class SeriesTrackingSyncScheduler {
    private static final Duration STALE_AFTER = Duration.ofHours(24);

    private final Executor executor;
    private final SeriesTrackingSyncService syncService;
    private final UserMediaRepository userMediaRepository;
    private final SeriesSeasonRepository seasonRepository;
    private final ExternalReferenceRepository referenceRepository;
    private final BackgroundJobRunner jobRunner;
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    public SeriesTrackingSyncScheduler(
            @Qualifier("externalInfoTaskExecutor") Executor executor,
            SeriesTrackingSyncService syncService,
            UserMediaRepository userMediaRepository,
            SeriesSeasonRepository seasonRepository,
            ExternalReferenceRepository referenceRepository,
            BackgroundJobRunner jobRunner
    ) {
        this.executor = executor;
        this.syncService = syncService;
        this.userMediaRepository = userMediaRepository;
        this.seasonRepository = seasonRepository;
        this.referenceRepository = referenceRepository;
        this.jobRunner = jobRunner;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void trackingRequested(SeriesTrackingRequestedEvent event) {
        schedule(event.seriesId());
    }

    @Scheduled(cron = "${catalog.series-tracking.cron}", zone = "${catalog.series-tracking.zone}")
    public void refreshTrackedSeries() {
        jobRunner.execute(JobKey.SERIES_TRACKING_SCAN, () -> {
            List<UUID> seriesIds = userMediaRepository.findDistinctSeriesIdsByStatus(UserMediaStatus.IN_PROGRESS);
            seriesIds.forEach(this::schedule);
            return BackgroundJobTracker.JobRunResult.completed(seriesIds.size(), seriesIds.size(),
                    seriesIds.size(), 0, "Tracked series refresh scheduled");
        });
    }

    public void scheduleIfStale(UUID seriesId) {
        List<SeriesSeason> seasons = seasonRepository.findAllBySeriesIdOrderBySeasonNumberAsc(seriesId).stream()
                .filter(season -> season.getSeasonNumber() != null && season.getSeasonNumber() > 0)
                .toList();
        Instant cutoff = Instant.now().minus(STALE_AFTER);
        Instant lastSyncedAt = referenceRepository.findByMediaIdAndSource(seriesId, ExternalSource.TMDB)
                .map(reference -> reference.getLastSyncedAt())
                .orElse(null);
        if (lastSyncedAt == null || lastSyncedAt.isBefore(cutoff)
                || seasons.stream().anyMatch(season -> season.getEpisodesSyncedAt() == null)) {
            schedule(seriesId);
        }
    }

    public void schedule(UUID seriesId) {
        if (!inFlight.add(seriesId)) return;
        try {
            executor.execute(() -> {
                try {
                    jobRunner.execute(JobKey.SERIES_TRACKING_SYNC, () -> {
                        syncService.synchronize(seriesId);
                        return BackgroundJobTracker.JobRunResult.completed(1, 1, 1, 0,
                                "Tracked series refreshed");
                    });
                } catch (RuntimeException exception) {
                    log.warn("Unable to synchronize tracked series {}: {}", seriesId, exception.getMessage());
                } finally {
                    inFlight.remove(seriesId);
                }
            });
        } catch (RuntimeException exception) {
            inFlight.remove(seriesId);
            log.warn("Unable to schedule tracked series {}", seriesId, exception);
        }
    }
}
