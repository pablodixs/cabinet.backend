package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.media.enums.AwardSubjectType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

@Service
@Slf4j
public class AwardRefreshScheduler {
    private final Executor executor;
    private final AwardRefreshWorker worker;
    private final Set<RefreshKey> inFlight = ConcurrentHashMap.newKeySet();

    public AwardRefreshScheduler(
            @Qualifier("externalInfoTaskExecutor") Executor executor,
            AwardRefreshWorker worker
    ) {
        this.executor = executor;
        this.worker = worker;
    }

    public void scheduleMedia(UUID mediaId) {
        schedule(new RefreshKey(AwardSubjectType.MEDIA, mediaId), () -> worker.refreshMedia(mediaId));
    }

    public void schedulePerson(UUID personId) {
        schedule(new RefreshKey(AwardSubjectType.PERSON, personId), () -> worker.refreshPerson(personId));
    }

    private void schedule(RefreshKey key, Runnable task) {
        if (!inFlight.add(key)) return;
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
            log.warn("Unable to schedule award refresh for {}", key, exception);
        }
    }

    private record RefreshKey(AwardSubjectType type, UUID id) {
    }
}
