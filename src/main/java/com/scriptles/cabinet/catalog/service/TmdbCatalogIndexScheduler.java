package com.scriptles.cabinet.catalog.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TmdbCatalogIndexScheduler {
    private final CatalogJobOrchestrationService orchestrationService;

    @Scheduled(cron = "${catalog.tmdb.index.cron:0 15 9 * * *}", zone = "${catalog.tmdb.index.zone:UTC}")
    public void enqueueDailyIndex() {
        var operation = orchestrationService.enqueueDailyCollectionIndex();
        log.info("Queued TMDB collection index operation {} for export {}",
                operation.operationId(), operation.externalId());
    }
}
