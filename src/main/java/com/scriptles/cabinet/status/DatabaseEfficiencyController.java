package com.scriptles.cabinet.status;

import com.scriptles.cabinet.catalog.repository.CatalogJobRepository;
import com.scriptles.cabinet.catalog.service.RollingCatalogMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/v1/admin/database-efficiency")
@RequiredArgsConstructor
public class DatabaseEfficiencyController {
    private static final String QUERY = "select left(query, 240) as query, calls, rows, total_exec_time, "
            + "mean_exec_time, max_exec_time from pg_stat_statements "
            + "where dbid = (select oid from pg_database where datname = current_database()) "
            + "and query not ilike '%%pg_stat_statements%%' order by %s desc limit 10";
    private static final List<String> SEARCH_STAGES = List.of(
            "api", "tmdb", "musicbrainz", "google_books", "enrichment", "rating");

    private final JdbcTemplate jdbcTemplate;
    private final CatalogJobRepository jobs;
    private final MeterRegistry meterRegistry;
    private final RollingCatalogMetrics rollingCatalogMetrics;

    @GetMapping
    @PreAuthorize("@communityAuthorization.isAdmin(authentication)")
    public DatabaseEfficiencyResponse get() {
        List<DatabaseEfficiencyResponse.QueryStat> byRows;
        List<DatabaseEfficiencyResponse.QueryStat> byCalls;
        List<DatabaseEfficiencyResponse.QueryStat> byTime;
        List<DatabaseEfficiencyResponse.QueryStat> byAverageTime;
        Instant statsResetAt = null;
        boolean available = true;
        String unavailableReason = null;
        try {
            byRows = queryStats("rows");
            byCalls = queryStats("calls");
            byTime = queryStats("total_exec_time");
            byAverageTime = queryStats("mean_exec_time");
            OffsetDateTime reset = jdbcTemplate.queryForObject(
                    "select stats_reset from pg_stat_statements_info", OffsetDateTime.class);
            statsResetAt = reset == null ? null : reset.toInstant();
        } catch (DataAccessException unavailableStats) {
            available = false;
            unavailableReason = "pg_stat_statements está indisponível ou sem permissão de leitura nesta instalação";
            byRows = List.of();
            byCalls = List.of();
            byTime = List.of();
            byAverageTime = List.of();
        }
        Instant now = Instant.now();
        return new DatabaseEfficiencyResponse(
                now,
                rollingCatalogMetrics.startedAt(),
                statsResetAt,
                available,
                unavailableReason,
                jobs.countByStatus("PENDING") + jobs.countByStatus("RETRY"),
                jobs.countByStatus("PROCESSING"),
                jobs.countByStatusAndCompletedAtAfter("COMPLETED", now.minus(24, ChronoUnit.HOURS)),
                rollingCatalogMetrics.mediaLastHour(),
                rollingCatalogMetrics.creditsLastHour(),
                rollingCatalogMetrics.peopleLastHour(),
                counter("cabinet.catalog.media.materialized"),
                counter("cabinet.catalog.credits.persisted"),
                counter("cabinet.catalog.people.resolved"),
                counter("cabinet.recommendations.candidates"),
                counter("cabinet.recommendations.credits_loaded"),
                counter("cabinet.interest_graph.media"),
                counter("cabinet.interest_graph.credits_loaded"),
                byRows,
                byCalls,
                byTime,
                byAverageTime,
                SEARCH_STAGES.stream().map(this::searchTiming).toList(),
                gauge("hikaricp.connections.active"),
                gauge("hikaricp.connections.idle"),
                gauge("hikaricp.connections.pending")
        );
    }

    private List<DatabaseEfficiencyResponse.QueryStat> queryStats(String orderBy) {
        return jdbcTemplate.query(QUERY.formatted(orderBy), (rs, row) ->
                new DatabaseEfficiencyResponse.QueryStat(
                        rs.getString("query"), rs.getLong("calls"), rs.getLong("rows"),
                        rs.getDouble("total_exec_time"), rs.getDouble("mean_exec_time"),
                        rs.getDouble("max_exec_time")));
    }

    private DatabaseEfficiencyResponse.SearchTiming searchTiming(String stage) {
        Timer timer = meterRegistry.find("cabinet.search.duration").tag("stage", stage).timer();
        return timer == null
                ? new DatabaseEfficiencyResponse.SearchTiming(stage, 0, 0, 0)
                : new DatabaseEfficiencyResponse.SearchTiming(stage, timer.count(),
                        timer.mean(TimeUnit.MILLISECONDS), timer.max(TimeUnit.MILLISECONDS));
    }

    private double gauge(String name) {
        var gauge = meterRegistry.find(name).gauge();
        return gauge == null ? 0 : gauge.value();
    }

    private double counter(String name) {
        var counter = meterRegistry.find(name).counter();
        return counter == null ? 0.0 : counter.count();
    }
}
