package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.enums.SeriesStatus;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

@Component
public class CatalogStalenessPolicy {
    @Value("${catalog.staleness.movie.future:24h}")
    private Duration movieFuture = Duration.ofHours(24);
    @Value("${catalog.staleness.movie.recent:3d}")
    private Duration movieRecent = Duration.ofDays(3);
    @Value("${catalog.staleness.movie.catalog:30d}")
    private Duration movieCatalog = Duration.ofDays(30);
    @Value("${catalog.staleness.series.active:24h}")
    private Duration seriesActive = Duration.ofHours(24);
    @Value("${catalog.staleness.series.inactive:30d}")
    private Duration seriesInactive = Duration.ofDays(30);

    public boolean isStale(Media media, Instant lastSyncedAt, SeriesStatus seriesStatus) {
        if (lastSyncedAt == null) return true;
        Duration threshold = threshold(media, seriesStatus);
        return lastSyncedAt.isBefore(Instant.now().minus(threshold));
    }

    public Duration threshold(Media media, SeriesStatus seriesStatus) {
        if (media.getType() == MediaType.SERIES) {
            return seriesStatus == SeriesStatus.AIRING || seriesStatus == SeriesStatus.PLANNED
                    ? seriesActive : seriesInactive;
        }
        LocalDate releaseDate = media.getReleaseDate();
        if (releaseDate == null || releaseDate.isAfter(LocalDate.now())) return movieFuture;
        return releaseDate.isAfter(LocalDate.now().minusDays(90)) ? movieRecent : movieCatalog;
    }
}
