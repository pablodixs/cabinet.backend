package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.media.enrichment.CatalogOutboxPublisher;
import com.scriptles.cabinet.media.entity.CatalogSyncCheckpoint;
import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.enums.CatalogSyncReason;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.external.TmdbClient;
import com.scriptles.cabinet.media.repository.CatalogSyncCheckpointRepository;
import com.scriptles.cabinet.media.repository.ExternalReferenceRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class TmdbChangesSyncScheduler {
    private static final int MAX_WINDOW_DAYS = 14;
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private final TmdbClient tmdbClient;
    private final CatalogSyncCheckpointRepository checkpointRepository;
    private final ExternalReferenceRepository referenceRepository;
    private final CatalogOutboxPublisher publisher;
    private final MeterRegistry meterRegistry;
    @Value("${catalog.tmdb-changes.batch-size:500}")
    private int batchSize = 500;
    @Value("${catalog.tmdb-changes.initial-window:2d}")
    private java.time.Duration initialWindow = java.time.Duration.ofDays(2);

    @Scheduled(cron = "${catalog.tmdb-changes.cron:0 30 3 * * *}",
            zone = "${catalog.tmdb-changes.zone:America/Sao_Paulo}")
    public void refreshChangedCatalog() {
        try {
            syncType(MediaType.MOVIE);
        } catch (RuntimeException failure) {
            log.warn("TMDB movie change synchronization failed: {}", failure.getMessage());
        }
        try {
            syncType(MediaType.SERIES);
        } catch (RuntimeException failure) {
            log.warn("TMDB series change synchronization failed: {}", failure.getMessage());
        }
    }

    @Transactional
    void syncType(MediaType mediaType) {
        LocalDate today = LocalDate.now(ZONE);
        LocalDate end = today.minusDays(1);
        CatalogSyncCheckpoint checkpoint = checkpointRepository
                .findBySourceAndMediaType(ExternalSource.TMDB, mediaType)
                .orElseGet(() -> {
                    CatalogSyncCheckpoint created = new CatalogSyncCheckpoint();
                    created.setSource(ExternalSource.TMDB);
                    created.setMediaType(mediaType);
                    return created;
                });
        LocalDate start = checkpoint.getLastCompletedEndDate() == null
                ? end.minusDays(Math.max(1, initialWindow.toDays() - 1))
                : checkpoint.getLastCompletedEndDate();
        if (start.isAfter(end)) return;

        while (!start.isAfter(end)) {
            LocalDate windowEnd = start.plusDays(MAX_WINDOW_DAYS - 1L);
            if (windowEnd.isAfter(end)) windowEnd = end;
            List<String> changedIds = fetchIds(mediaType, start, windowEnd);
            meterRegistry.counter("cabinet.tmdb.changes.ids", "media_type", mediaType.name())
                    .increment(changedIds.size());
            publishMatching(mediaType, changedIds);
            checkpoint.setLastCompletedEndDate(windowEnd);
            checkpointRepository.save(checkpoint);
            start = windowEnd.plusDays(1);
        }
    }

    private List<String> fetchIds(MediaType mediaType, LocalDate start, LocalDate end) {
        List<String> ids = new ArrayList<>();
        TmdbClient.TmdbChangePage page = mediaType == MediaType.MOVIE
                ? tmdbClient.findChangedMoviePage(start, end, 1)
                : tmdbClient.findChangedSeriesPage(start, end, 1);
        ids.addAll(page.ids());
        for (int current = 2; current <= page.totalPages(); current++) {
            TmdbClient.TmdbChangePage next = mediaType == MediaType.MOVIE
                    ? tmdbClient.findChangedMoviePage(start, end, current)
                    : tmdbClient.findChangedSeriesPage(start, end, current);
            ids.addAll(next.ids());
        }
        return List.copyOf(new LinkedHashSet<>(ids));
    }

    private void publishMatching(MediaType mediaType, List<String> changedIds) {
        Set<String> unique = new LinkedHashSet<>(changedIds);
        List<String> ids = new ArrayList<>(unique);
        for (int offset = 0; offset < ids.size(); offset += batchSize) {
            List<String> batch = ids.subList(offset, Math.min(offset + batchSize, ids.size()));
            List<ExternalReference> matches = referenceRepository
                    .findAllBySourceAndExternalIdIn(ExternalSource.TMDB, batch).stream()
                    .filter(reference -> reference.getMedia().getType() == mediaType)
                    .toList();
            meterRegistry.counter("cabinet.tmdb.changes.matched", "media_type", mediaType.name())
                    .increment(matches.size());
            for (ExternalReference reference : matches) {
                publisher.publishRefresh(reference.getMedia().getId(), ExternalSource.TMDB,
                        reference.getExternalId(), mediaType, reference.getMedia().getDefaultLocale(),
                        CatalogSyncReason.PROVIDER_CHANGE);
            }
        }
    }
}
