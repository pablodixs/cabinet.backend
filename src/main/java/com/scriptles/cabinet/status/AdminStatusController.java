package com.scriptles.cabinet.status;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.validation.annotation.Validated;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/v1/admin/status")
@RequiredArgsConstructor
@Validated
@PreAuthorize("@communityAuthorization.isAdmin(authentication)")
public class AdminStatusController {
    private final JdbcTemplate jdbc;
    private final MeterRegistry meters;
    private final BackgroundJobRunRepository jobRuns;

    @GetMapping
    public AdminOverview overview() {
        List<QueueStatus> queues = List.of(
                queue("domain-outbox", "domain_outbox_events"),
                queue("catalog-outbox", "catalog_outbox"),
                queue("catalog-jobs", "catalog_jobs")
        );
        List<ProviderStatus> providers = providerStatuses();
        Timer apiSearch = meters.find("cabinet.search.duration").tag("stage", "api").timer();
        double searches = apiSearch == null ? 0 : apiSearch.count();
        double fallback = counter("cabinet.search.provider.fallback", Map.of());
        double zeroResults = counter("cabinet.search.zero_results", Map.of());
        SearchStatus search = new SearchStatus(
                searches,
                counter("cabinet.search.failure", Map.of()),
                fallback,
                searches == 0 ? 0 : fallback * 100d / searches,
                zeroResults,
                searches == 0 ? 0 : zeroResults * 100d / searches,
                apiSearch == null ? 0 : apiSearch.mean(java.util.concurrent.TimeUnit.MILLISECONDS));
        DatabasePoolStatus pool = new DatabasePoolStatus(
                gauge("hikaricp.connections.active"), gauge("hikaricp.connections.idle"),
                gauge("hikaricp.connections.pending"), gauge("hikaricp.connections.max"),
                timerMean("hikaricp.connections.acquire"));
        List<CacheStatus> caches = cacheStatuses();
        List<SyncRun> lastSync = jobRuns.findAllByFinishedAtIsNotNullOrderByFinishedAtDesc(
                        org.springframework.data.domain.PageRequest.of(0, 50)).stream()
                .collect(Collectors.toMap(BackgroundJobRun::getJobKey, this::syncRun, (first, ignored) -> first))
                .values().stream().sorted(Comparator.comparing(SyncRun::startedAt).reversed()).toList();
        return new AdminOverview(Instant.now(), queues, lastSync, providers, search, pool, caches,
                gauge("cabinet.community.lag.seconds"));
    }

    @GetMapping("/jobs")
    public List<SyncRun> jobs(@RequestParam(required = false) String key,
                              @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
        if (key != null && !key.matches("[A-Z_]{1,50}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid job key");
        }
        String sql = """
                select id, job_key, status, started_at, finished_at, processed_count, updated_count, failure_count
                from background_job_runs
                where 1 = 1
                order by started_at desc limit ?
                """;
        List<Object> params = new ArrayList<>();
        if (key != null) {
            sql = sql.replace("where 1 = 1", "where job_key = ?");
            params.add(key);
        }
        params.add(limit);
        return jdbc.query(sql, (rs, row) -> new SyncRun(rs.getObject("id", UUID.class), rs.getString("job_key"),
                rs.getString("status"), rs.getTimestamp("started_at").toInstant(),
                rs.getTimestamp("finished_at") == null ? null : rs.getTimestamp("finished_at").toInstant(),
                rs.getInt("processed_count"), rs.getInt("updated_count"), rs.getInt("failure_count")), params.toArray());
    }

    @GetMapping("/jobs/{id}")
    public SyncRun job(@PathVariable UUID id) {
        return jdbc.query("""
                        select id, job_key, status, started_at, finished_at, processed_count, updated_count, failure_count
                        from background_job_runs where id = ?
                        """, (rs, row) -> new SyncRun(rs.getObject("id", UUID.class), rs.getString("job_key"),
                        rs.getString("status"), rs.getTimestamp("started_at").toInstant(),
                        rs.getTimestamp("finished_at") == null ? null : rs.getTimestamp("finished_at").toInstant(),
                        rs.getInt("processed_count"), rs.getInt("updated_count"), rs.getInt("failure_count")), id)
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @GetMapping("/events")
    public List<EventSummary> events(@RequestParam(defaultValue = "domain") String queue,
                                    @RequestParam(required = false) String status,
                                    @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
        return eventQuery(queue, status, limit, null, null);
    }

    @GetMapping("/events/{queue}/{id}")
    public EventSummary event(@PathVariable String queue, @PathVariable UUID id) {
        return eventQuery(queue, null, 1, id, null).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @GetMapping("/media/{mediaId}")
    public MediaDrilldown media(@PathVariable UUID mediaId,
                                @RequestParam(defaultValue = "25") @Min(1) @Max(100) int limit) {
        Instant lastSync = jdbc.queryForObject("""
                select max(synced_at)
                from (
                    select last_synced_at as synced_at from external_references where media_id = ?
                    union all
                    select last_synced_at as synced_at from album_release_versions where album_media_id = ?
                ) sync_times
                """, (rs, row) -> instant(rs.getTimestamp(1)), mediaId, mediaId);
        long domainEvents = jdbc.queryForObject("""
                select count(*) from domain_outbox_events where aggregate_type = 'MEDIA' and aggregate_id = ?
                """, Long.class, mediaId);
        long catalogJobs = jdbc.queryForObject("select count(*) from catalog_jobs where media_id = ?", Long.class,
                mediaId);
        List<EventSummary> recent = new ArrayList<>();
        recent.addAll(eventQuery("domain", null, limit, null, mediaId));
        recent.addAll(eventQuery("catalog", null, limit, null, mediaId));
        recent.sort(Comparator.comparing(EventSummary::createdAt).reversed());
        return new MediaDrilldown(mediaId, lastSync, domainEvents, catalogJobs,
                recent.stream().limit(limit).toList());
    }

    @GetMapping("/providers/{provider}")
    public ProviderStatus provider(@PathVariable String provider) {
        String normalized = provider.toUpperCase(java.util.Locale.ROOT);
        if (!Set.of("TMDB", "IMDB", "GOOGLE_BOOKS", "OPEN_LIBRARY", "MUSICBRAINZ", "SPOTIFY",
                "APPLE_MUSIC", "DEEZER", "LAST_FM", "WIKIDATA", "JUSTWATCH", "OMDB",
                "ROTTEN_TOMATOES", "METACRITIC", "LETTERBOXD", "MANUAL").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return providerStatus(normalized);
    }

    @GetMapping("/catalog-jobs/{id}")
    public CatalogJobDetail catalogJob(@PathVariable UUID id) {
        return jdbc.query("""
                select id, job_type, provider, status, attempts, media_id, collection_id,
                       created_at, started_at, completed_at, available_at
                from catalog_jobs where id = ?
                """, (rs, row) -> new CatalogJobDetail(rs.getObject("id", UUID.class), rs.getString("job_type"),
                rs.getString("provider"), rs.getString("status"), rs.getInt("attempts"),
                rs.getObject("media_id", UUID.class), rs.getObject("collection_id", UUID.class),
                instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("started_at")),
                instant(rs.getTimestamp("completed_at")), instant(rs.getTimestamp("available_at"))), id)
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private List<EventSummary> eventQuery(String queue, String status, int limit, UUID id, UUID mediaId) {
        boolean domain = switch (queue) {
            case "domain" -> true;
            case "catalog" -> false;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Queue must be domain or catalog");
        };
        String table = domain ? "domain_outbox_events" : "catalog_outbox";
        String eventType = "event_type";
        String aggregateType = domain ? "aggregate_type" : "'MEDIA'";
        String aggregateId = "aggregate_id";
        String attempts = domain ? "attempt_count" : "attempts";
        String processing = domain ? "processing_started_at" : "locked_at";
        Set<String> allowed = domain
                ? Set.of("PENDING", "PROCESSING", "RETRY", "COMPLETED", "DEAD")
                : Set.of("PENDING", "PROCESSING", "RETRY", "PROCESSED", "DEAD");
        if (status != null && !allowed.contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid event status");
        }
        String sql = """
                select id, %s as event_type, %s as aggregate_type, %s as aggregate_id, status,
                       %s as attempts, available_at, created_at, %s as processing_started_at, processed_at
                from %s
                where 1 = 1
                """.formatted(eventType, aggregateType, aggregateId, attempts, processing, table);
        List<Object> parameters = new ArrayList<>();
        if (status != null) {
            sql += " and status = ?";
            parameters.add(status);
        }
        if (id != null) {
            sql += " and id = ?";
            parameters.add(id);
        }
        if (mediaId != null) {
            sql += " and %s = 'MEDIA' and %s = ?".formatted(aggregateType, aggregateId);
            parameters.add(mediaId);
        }
        sql += " order by created_at desc limit ?";
        parameters.add(limit);
        return jdbc.query(sql, (rs, row) -> new EventSummary(
                rs.getObject("id", UUID.class), queue, rs.getString("event_type"), rs.getString("status"),
                rs.getInt("attempts"), "MEDIA".equals(rs.getString("aggregate_type"))
                        ? rs.getObject("aggregate_id", UUID.class) : null,
                instant(rs.getTimestamp("available_at")), instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("processing_started_at")), instant(rs.getTimestamp("processed_at"))),
                parameters.toArray());
    }

    private QueueStatus queue(String name, String table) {
        return jdbc.queryForObject("""
                select count(*) filter (where status = 'PENDING') as pending,
                       count(*) filter (where status = 'PROCESSING') as processing,
                       count(*) filter (where status = 'RETRY') as retry,
                       count(*) filter (where status = 'DEAD') as dead,
                       min(created_at) filter (where status in ('PENDING', 'RETRY')) as oldest
                from %s where status in ('PENDING', 'PROCESSING', 'RETRY', 'DEAD')
                """.formatted(table), (rs, row) -> {
            java.sql.Timestamp oldest = rs.getTimestamp("oldest");
            return new QueueStatus(name, rs.getLong("pending"), rs.getLong("processing"), rs.getLong("retry"),
                    rs.getLong("dead"), instant(oldest), oldest == null ? 0
                    : Math.max(0, Instant.now().toEpochMilli() - oldest.getTime()) / 1000d);
        });
    }

    private List<ProviderStatus> providerStatuses() {
        Set<String> providers = meters.getMeters().stream()
                .filter(meter -> meter.getId().getName().equals("cabinet.provider.requests"))
                .map(meter -> meter.getId().getTag("provider"))
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        return providers.stream().sorted().map(this::providerStatus).toList();
    }

    private ProviderStatus providerStatus(String provider) {
        double requests = counter("cabinet.provider.requests", Map.of("provider", provider));
        double failures = counter("cabinet.provider.failures", Map.of("provider", provider));
        double limited = counter("cabinet.provider.rate_limited", Map.of("provider", provider));
        double durationCount = 0;
        double durationTotal = 0;
        for (Meter meter : meters.getMeters()) {
            if (meter instanceof Timer timer && meter.getId().getName().equals("cabinet.provider.duration")
                    && provider.equals(meter.getId().getTag("provider"))) {
                durationCount += timer.count();
                durationTotal += timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS);
            }
        }
        return new ProviderStatus(provider, requests, failures, limited,
                requests == 0 ? 0 : failures * 100d / requests,
                durationCount == 0 ? 0 : durationTotal / durationCount);
    }

    private List<CacheStatus> cacheStatuses() {
        Map<String, Map<String, Double>> values = new HashMap<>();
        for (Meter meter : meters.getMeters()) {
            if (!meter.getId().getName().startsWith("cabinet.cache.")) continue;
            String cache = meter.getId().getTag("cache");
            if (cache == null || !(meter instanceof Gauge gauge)) continue;
            String statistic = meter.getId().getName().substring("cabinet.cache.".length());
            values.computeIfAbsent(cache, ignored -> new HashMap<>()).put(statistic, gauge.value());
        }
        return values.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry -> {
            Map<String, Double> stats = entry.getValue();
            return new CacheStatus(entry.getKey(), stats.getOrDefault("hit", 0d),
                    stats.getOrDefault("miss", 0d), stats.getOrDefault("eviction", 0d),
                    stats.getOrDefault("size", 0d));
        }).toList();
    }

    private SyncRun syncRun(BackgroundJobRun run) {
        return new SyncRun(run.getId(), run.getJobKey().name(), run.getStatus().name(), run.getStartedAt(),
                run.getFinishedAt(), run.getProcessedCount(), run.getUpdatedCount(), run.getFailureCount());
    }

    private double counter(String name, Map<String, String> tags) {
        return meters.getMeters().stream().filter(meter -> meter instanceof Counter)
                .filter(meter -> meter.getId().getName().equals(name))
                .filter(meter -> tags.entrySet().stream().allMatch(tag ->
                        tag.getValue().equals(meter.getId().getTag(tag.getKey()))))
                .mapToDouble(meter -> ((Counter) meter).count()).sum();
    }

    private double gauge(String name) {
        return meters.getMeters().stream().filter(meter -> meter instanceof Gauge)
                .filter(meter -> meter.getId().getName().equals(name))
                .mapToDouble(meter -> ((Gauge) meter).value()).findFirst().orElse(0d);
    }

    private double timerMean(String name) {
        return meters.getMeters().stream().filter(meter -> meter instanceof Timer)
                .filter(meter -> meter.getId().getName().equals(name))
                .mapToDouble(meter -> ((Timer) meter).mean(java.util.concurrent.TimeUnit.MILLISECONDS))
                .findFirst().orElse(0d);
    }

    private static Instant instant(java.sql.Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    public record AdminOverview(Instant updatedAt, List<QueueStatus> queues, List<SyncRun> lastSync,
                                List<ProviderStatus> providers, SearchStatus search, DatabasePoolStatus databasePool,
                                List<CacheStatus> caches, double communityLagSeconds) {}
    public record QueueStatus(String queue, long pending, long processing, long retry, long dead,
                              Instant oldestPendingAt, double oldestPendingSeconds) {}
    public record SyncRun(UUID id, String jobKey, String status, Instant startedAt, Instant finishedAt,
                          int processed, int updated, int failed) {}
    public record ProviderStatus(String provider, double requests, double failures, double rateLimited,
                                 double errorRatePercent, double meanDurationMs) {}
    public record SearchStatus(double requests, double failures, double providerFallbacks,
                               double providerFallbackRatePercent, double zeroResults,
                               double zeroResultRatePercent, double meanDurationMs) {}
    public record DatabasePoolStatus(double active, double idle, double pending, double max,
                                     double meanAcquisitionMs) {}
    public record CacheStatus(String name, double hit, double miss, double eviction, double size) {}
    public record EventSummary(UUID id, String queue, String eventType, String status, int attempts,
                               UUID mediaId, Instant availableAt, Instant createdAt,
                               Instant processingStartedAt, Instant processedAt) {}
    public record MediaDrilldown(UUID mediaId, Instant lastSyncAt, long domainEventCount, long catalogJobCount,
                                 List<EventSummary> recentEvents) {}
    public record CatalogJobDetail(UUID id, String jobType, String provider, String status, int attempts,
                                   UUID mediaId, UUID collectionId, Instant createdAt, Instant startedAt,
                                   Instant completedAt, Instant availableAt) {}
}
