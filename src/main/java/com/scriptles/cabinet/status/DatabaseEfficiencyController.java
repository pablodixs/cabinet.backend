package com.scriptles.cabinet.status;

import com.scriptles.cabinet.catalog.repository.CatalogJobRepository;
import com.scriptles.cabinet.catalog.service.RollingCatalogMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RestController
@RequestMapping("/v1/admin/database-efficiency")
@RequiredArgsConstructor
public class DatabaseEfficiencyController {
    private static final String QUERY = "select left(query, 240) as query, calls, rows, total_exec_time "
            + "from pg_stat_statements where query ilike '%media_credits%' "
            + "and query not ilike '%pg_stat_statements%' order by %s desc limit 10";

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
        boolean available = true;
        String unavailableReason = null;
        try {
            byRows = queryStats("rows");
            byCalls = queryStats("calls");
            byTime = queryStats("total_exec_time");
        } catch (DataAccessException unavailableStats) {
            available = false;
            unavailableReason = "A aplicação não tem permissão para ler pg_stat_statements nesta instalação";
            byRows = List.of();
            byCalls = List.of();
            byTime = List.of();
        }
        Instant now = Instant.now();
        return new DatabaseEfficiencyResponse(
                now,
                rollingCatalogMetrics.startedAt(),
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
                byTime
        );
    }

    private List<DatabaseEfficiencyResponse.QueryStat> queryStats(String orderBy) {
        return jdbcTemplate.query(QUERY.formatted(orderBy), (rs, row) ->
                new DatabaseEfficiencyResponse.QueryStat(
                        rs.getString("query"), rs.getLong("calls"), rs.getLong("rows"),
                        rs.getDouble("total_exec_time")));
    }

    private double counter(String name) {
        var counter = meterRegistry.find(name).counter();
        return counter == null ? 0.0 : counter.count();
    }
}
