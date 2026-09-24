package com.scriptles.cabinet.status;

import com.scriptles.cabinet.catalog.repository.CatalogJobRepository;
import com.scriptles.cabinet.catalog.service.RollingCatalogMetrics;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.dao.PermissionDeniedDataAccessException;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatabaseEfficiencyControllerTest {
    @Test
    void exposesAllDatabaseQueriesAndSearchStageTimings() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        CatalogJobRepository jobs = mock(CatalogJobRepository.class);
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        Timer.builder("cabinet.search.duration").tag("stage", "tmdb")
                .register(meters).record(Duration.ofMillis(120));
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<DatabaseEfficiencyResponse.QueryStat>>any()))
                .thenReturn(List.of());
        OffsetDateTime reset = OffsetDateTime.parse("2026-09-24T12:00:00Z");
        when(jdbc.queryForObject("select stats_reset from pg_stat_statements_info", OffsetDateTime.class))
                .thenReturn(reset);

        DatabaseEfficiencyResponse response = new DatabaseEfficiencyController(
                jdbc, jobs, meters, new RollingCatalogMetrics()).get();

        assertThat(response.pgStatsAvailable()).isTrue();
        assertThat(response.pgStatsResetAt()).isEqualTo(reset.toInstant());
        assertThat(response.searchTimings()).filteredOn(timing -> timing.stage().equals("tmdb"))
                .singleElement().satisfies(timing -> {
                    assertThat(timing.calls()).isEqualTo(1);
                    assertThat(timing.averageMs()).isEqualTo(120);
                });
        verify(jdbc).query(org.mockito.ArgumentMatchers.contains("order by mean_exec_time desc"),
                org.mockito.ArgumentMatchers.<RowMapper<DatabaseEfficiencyResponse.QueryStat>>any());
        verify(jdbc, times(4)).query(org.mockito.ArgumentMatchers.contains("'%pg_stat_statements%'"),
                org.mockito.ArgumentMatchers.<RowMapper<DatabaseEfficiencyResponse.QueryStat>>any());
    }

    @Test
    void keepsSearchTimingsAvailableWhenPostgresStatisticsCannotBeRead() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        Timer.builder("cabinet.search.duration").tag("stage", "api")
                .register(meters).record(Duration.ofMillis(80));
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<DatabaseEfficiencyResponse.QueryStat>>any()))
                .thenThrow(new PermissionDeniedDataAccessException("pg_stat_statements", new Exception()));

        DatabaseEfficiencyResponse response = new DatabaseEfficiencyController(
                jdbc, mock(CatalogJobRepository.class), meters, new RollingCatalogMetrics()).get();

        assertThat(response.pgStatsAvailable()).isFalse();
        assertThat(response.topByExecutionTime()).isEmpty();
        assertThat(response.searchTimings()).filteredOn(timing -> timing.stage().equals("api"))
                .singleElement().satisfies(timing -> assertThat(timing.calls()).isEqualTo(1));
    }
}
