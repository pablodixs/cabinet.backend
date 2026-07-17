package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.media.enums.ExternalInfoKind;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

@Service
@Slf4j
public class ExternalInfoRefreshScheduler {
    private final Executor executor;
    private final ExternalInfoRefreshWorker worker;
    private final Set<RefreshKey> inFlight = ConcurrentHashMap.newKeySet();

    public ExternalInfoRefreshScheduler(
            @Qualifier("externalInfoTaskExecutor") Executor executor,
            ExternalInfoRefreshWorker worker
    ) {
        this.executor = executor;
        this.worker = worker;
    }

    public void scheduleAvailability(UUID mediaId, String countryCode) {
        schedule(new RefreshKey(mediaId, ExternalInfoKind.AVAILABILITY, countryCode),
                () -> worker.refreshAvailability(mediaId, countryCode));
    }

    public void scheduleRatings(UUID mediaId) {
        schedule(new RefreshKey(mediaId, ExternalInfoKind.RATINGS, MediaExternalInfoService.GLOBAL_REGION),
                () -> worker.refreshRatings(mediaId));
    }

    private void schedule(RefreshKey key, Runnable task) {
        if (!inFlight.add(key)) {
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    task.run();
                } finally {
                    inFlight.remove(key);
                }
            });
        } catch (RuntimeException exception) {
            inFlight.remove(key);
            log.warn("Unable to schedule external info refresh for {}", key, exception);
        }
    }

    private record RefreshKey(UUID mediaId, ExternalInfoKind kind, String region) {
    }
}
