package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.enums.SeriesStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogStalenessPolicyTest {
    private final CatalogStalenessPolicy policy = new CatalogStalenessPolicy();

    @Test
    void usesMovieReleaseBuckets() {
        Media future = media(MediaType.MOVIE, LocalDate.now().plusDays(1));
        Media recent = media(MediaType.MOVIE, LocalDate.now().minusDays(10));
        Media catalog = media(MediaType.MOVIE, LocalDate.now().minusDays(91));

        assertThat(policy.threshold(future, null)).isEqualTo(Duration.ofDays(1));
        assertThat(policy.threshold(recent, null)).isEqualTo(Duration.ofDays(3));
        assertThat(policy.threshold(catalog, null)).isEqualTo(Duration.ofDays(30));
    }

    @Test
    void usesSeriesStatusBucketsAndNullTimestampIsStale() {
        Media series = media(MediaType.SERIES, null);
        assertThat(policy.threshold(series, SeriesStatus.AIRING)).isEqualTo(Duration.ofDays(1));
        assertThat(policy.threshold(series, SeriesStatus.ENDED)).isEqualTo(Duration.ofDays(30));
        assertThat(policy.isStale(series, null, SeriesStatus.ENDED)).isTrue();
        assertThat(policy.isStale(series, Instant.now().minus(Duration.ofDays(31)), SeriesStatus.ENDED)).isTrue();
    }

    private Media media(MediaType type, LocalDate releaseDate) {
        Media media = new Media();
        media.setType(type);
        media.setTitle("Test");
        media.setReleaseDate(releaseDate);
        return media;
    }
}
