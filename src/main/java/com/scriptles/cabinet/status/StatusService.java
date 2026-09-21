package com.scriptles.cabinet.status;

import com.scriptles.cabinet.media.enums.CatalogOutboxStatus;
import com.scriptles.cabinet.media.repository.CatalogOutboxRepository;
import com.scriptles.cabinet.catalog.repository.CatalogJobRepository;
import com.scriptles.cabinet.user.importer.LetterboxdImportJob;
import com.scriptles.cabinet.user.importer.LetterboxdImportJobRepository;
import com.scriptles.cabinet.user.importer.LetterboxdImportJobState;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static com.scriptles.cabinet.status.StatusResponse.*;

@Service
@RequiredArgsConstructor
public class StatusService {
    private static final Duration FAILURE_WINDOW = Duration.ofHours(24);
    private static final int ACTIVITY_LIMIT = 15;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final BackgroundJobRunRepository runRepository;
    private final CatalogOutboxRepository outboxRepository;
    private final CatalogJobRepository catalogJobs;
    private final LetterboxdImportJobRepository importRepository;

    @Value("${catalog.tmdb-changes.cron}")
    private String tmdbCron;
    @Value("${catalog.tmdb-changes.zone}")
    private String tmdbZone;
    @Value("${catalog.collections.tmdb-sync.cron}")
    private String collectionsCron;
    @Value("${catalog.collections.tmdb-sync.zone}")
    private String collectionsZone;
    @Value("${catalog.series-tracking.cron}")
    private String seriesCron;
    @Value("${catalog.series-tracking.zone}")
    private String seriesZone;
    @Value("${app.notifications.episode-cron}")
    private String episodeNotificationsCron;
    @Value("${app.notifications.retention-cron}")
    private String notificationRetentionCron;
    @Value("${app.letterboxd.cleanup-cron}")
    private String letterboxdCleanupCron;

    @Transactional(readOnly = true)
    public StatusResponse getStatus() {
        Instant now = Instant.now();
        Map<JobKey, BackgroundJobRun> latest = new EnumMap<>(JobKey.class);
        List<JobDefinition> definitions = definitions();
        for (JobDefinition definition : definitions) {
            runRepository.findFirstByJobKeyOrderByStartedAtDesc(definition.key())
                    .ifPresent(run -> latest.put(definition.key(), run));
        }
        Map<JobKey, HealthStatus> health = new EnumMap<>(JobKey.class);
        definitions.forEach(definition -> health.put(definition.key(),
                definition.key() == JobKey.COLLECTION_SYNC
                        ? healthOnDemand(JobKey.COLLECTION_SYNC, latest.get(JobKey.COLLECTION_SYNC), now)
                        : health(definition, latest.get(definition.key()), now)));
        runRepository.findFirstByJobKeyOrderByStartedAtDesc(JobKey.SERIES_TRACKING_SYNC)
                .ifPresent(run -> latest.put(JobKey.SERIES_TRACKING_SYNC, run));
        health.put(JobKey.SERIES_TRACKING_SYNC,
                healthOnDemand(JobKey.SERIES_TRACKING_SYNC, latest.get(JobKey.SERIES_TRACKING_SYNC), now));

        Map<CatalogOutboxStatus, Integer> queue = new EnumMap<>(CatalogOutboxStatus.class);
        outboxRepository.countByStatus().forEach(count -> queue.put(count.getStatus(), safeInt(count.getCount())));
        int catalogProcessing = safeInt(catalogJobs.countByStatus("PROCESSING"));
        int catalogWaiting = safeInt(catalogJobs.countByStatus("PENDING"));
        int catalogRetrying = safeInt(catalogJobs.countByStatus("RETRY"));
        int catalogDead = safeInt(catalogJobs.countByStatus("DEAD"));
        BackgroundProcessingStatus background = new BackgroundProcessingStatus(
                value(queue, CatalogOutboxStatus.PROCESSING) + catalogProcessing,
                value(queue, CatalogOutboxStatus.PENDING) + catalogWaiting,
                value(queue, CatalogOutboxStatus.RETRY) + catalogRetrying,
                value(queue, CatalogOutboxStatus.DEAD) + catalogDead);

        int importsProcessing = safeInt(importRepository.countByStateIn(List.of(
                LetterboxdImportJobState.PARSING,
                LetterboxdImportJobState.MATCHING,
                LetterboxdImportJobState.IMPORTING)));
        int importsWaiting = safeInt(importRepository.countByStateIn(List.of(LetterboxdImportJobState.READY)));
        List<LetterboxdImportJob> recentImports = importRepository
                .findTop10ByStateInAndCompletedAtAfterOrderByCompletedAtDesc(
                        List.of(LetterboxdImportJobState.COMPLETED,
                                LetterboxdImportJobState.COMPLETED_WITH_ERRORS),
                        now.minus(FAILURE_WINDOW));
        int completedImports = safeInt(importRepository.countByStateInAndCompletedAtAfter(
                List.of(LetterboxdImportJobState.COMPLETED,
                        LetterboxdImportJobState.COMPLETED_WITH_ERRORS), now.minus(FAILURE_WINDOW)));
        ImportProcessingStatus imports = new ImportProcessingStatus(
                importsProcessing, importsWaiting, completedImports);

        List<ScheduledJobStatus> scheduled = definitions.stream()
                .map(definition -> scheduledJob(definition, latest.get(definition.key()), now))
                .toList();
        List<SynchronizationStatus> synchronizations = synchronizationStatuses(definitions, latest, health, now);
        List<SystemStatus> systems = systemStatuses(health, background);
        List<HealthStatus> overallStates = new ArrayList<>(health.values());
        systems.stream().map(SystemStatus::status).forEach(overallStates::add);
        HealthStatus overall = overallStates.stream()
                .reduce(HealthStatus.OPERATIONAL, StatusService::moreSevereForOverall);

        return new StatusResponse(overall, now, systems, synchronizations, background, imports,
                scheduled, recentActivity(recentImports));
    }

    private List<SystemStatus> systemStatuses(Map<JobKey, HealthStatus> health,
                                              BackgroundProcessingStatus background) {
        HealthStatus catalog = HealthStatus.OPERATIONAL;
        if (background.failed() > 0) catalog = HealthStatus.DEGRADED;
        else if (background.retrying() >= 25 || background.waiting() >= 1000) {
            catalog = HealthStatus.DELAYED;
        }

        HealthStatus synchronizations = worstHealth(List.of(
                health.get(JobKey.TMDB_CATALOG_SYNC), health.get(JobKey.COLLECTION_SYNC),
                health.get(JobKey.SERIES_TRACKING_SCAN), health.get(JobKey.SERIES_TRACKING_SYNC)));
        HealthStatus imports = health.get(JobKey.LETTERBOXD_CLEANUP);
        HealthStatus externalServices = worstHealth(List.of(
                health.get(JobKey.TMDB_CATALOG_SYNC), health.get(JobKey.COLLECTION_SYNC),
                health.get(JobKey.SERIES_TRACKING_SCAN), health.get(JobKey.SERIES_TRACKING_SYNC)));

        return List.of(
                new SystemStatus("CATALOG", "Catalog", catalog,
                        "Catalog enrichment and background processing"),
                new SystemStatus("SYNCHRONIZATIONS", "Synchronizations", synchronizations,
                        "TMDB catalog, collections and tracked series"),
                new SystemStatus("IMPORTS", "Imports", imports,
                        "Letterboxd imports and maintenance"),
                new SystemStatus("EXTERNAL_SERVICES", "External services", externalServices,
                        "Provider backed catalog updates"));
    }

    private List<SynchronizationStatus> synchronizationStatuses(List<JobDefinition> definitions,
                                                                  Map<JobKey, BackgroundJobRun> latest,
                                                                  Map<JobKey, HealthStatus> health,
                                                                  Instant now) {
        return List.of(
                synchronization("TMDB_CATALOG", "TMDB catalog", "Keeping movies and series up to date",
                        definition(definitions, JobKey.TMDB_CATALOG_SYNC), latest.get(JobKey.TMDB_CATALOG_SYNC),
                        health.get(JobKey.TMDB_CATALOG_SYNC), now),
                synchronization("COLLECTIONS", "Collections", "Keeping film collections up to date",
                        definition(definitions, JobKey.COLLECTION_SYNC), latest.get(JobKey.COLLECTION_SYNC),
                        health.get(JobKey.COLLECTION_SYNC), now),
                synchronization("SERIES_TRACKING", "Series tracking", "Checking tracked series for new episodes",
                        definition(definitions, JobKey.SERIES_TRACKING_SCAN), latest.get(JobKey.SERIES_TRACKING_SYNC),
                        worstHealth(List.of(health.get(JobKey.SERIES_TRACKING_SCAN),
                                health.get(JobKey.SERIES_TRACKING_SYNC))), now));
    }

    private SynchronizationStatus synchronization(String key, String name, String description,
                                                    JobDefinition definition, BackgroundJobRun run, Instant now) {
        return synchronization(key, name, description, definition, run,
                health(definition, run, now), now);
    }

    private SynchronizationStatus synchronization(String key, String name, String description,
                                                    JobDefinition definition, BackgroundJobRun run,
                                                    HealthStatus health, Instant now) {
        Long duration = run == null ? null : durationMillis(run, now);
        return new SynchronizationStatus(key, name, description, health,
                run == null ? null : run.getStartedAt(),
                definition.nextRunAt(now), duration,
                run == null ? null : run.getProcessedCount(),
                run == null ? null : run.getUpdatedCount(),
                run == null ? null : run.getFailureCount());
    }

    private ScheduledJobStatus scheduledJob(JobDefinition definition, BackgroundJobRun run, Instant now) {
        return new ScheduledJobStatus(definition.key().name(), definition.name(), definition.schedule(),
                run == null ? null : run.getStatus(), run == null ? null : run.getStartedAt(),
                definition.nextRunAt(now), run == null ? null : durationMillis(run, now));
    }

    private List<StatusActivity> recentActivity(List<LetterboxdImportJob> imports) {
        List<StatusActivity> activity = new ArrayList<>();
        runRepository.findAllByFinishedAtIsNotNullOrderByFinishedAtDesc(PageRequest.of(0, ACTIVITY_LIMIT))
                .stream()
                .filter(run -> run.getJobKey() != JobKey.SERIES_TRACKING_SCAN)
                .map(this::activity)
                .forEach(activity::add);
        imports.forEach(job -> activity.add(new StatusActivity(
                job.getState() == LetterboxdImportJobState.COMPLETED_WITH_ERRORS
                        ? "IMPORT_WITH_WARNINGS" : "IMPORT_COMPLETED",
                "IMPORTS", "LETTERBOXD_IMPORT", "Letterboxd import completed",
                job.getImportedItems() + " films imported", job.getCompletedAt(),
                job.getTotalItems(), job.getImportedItems(), job.getFailedItems())));
        return activity.stream()
                .filter(item -> item.occurredAt() != null)
                .sorted((left, right) -> right.occurredAt().compareTo(left.occurredAt()))
                .limit(ACTIVITY_LIMIT)
                .toList();
    }

    private StatusActivity activity(BackgroundJobRun run) {
        String category = switch (run.getJobKey()) {
            case TMDB_CATALOG_SYNC, COLLECTION_SYNC, SERIES_TRACKING_SYNC, SERIES_TRACKING_SCAN -> "SYNCHRONIZATIONS";
            case EPISODE_NOTIFICATIONS, NOTIFICATION_RETENTION -> "CATALOG";
            case LETTERBOXD_CLEANUP -> "IMPORTS";
        };
        String type = switch (run.getStatus()) {
            case COMPLETED -> "SYNC_COMPLETED";
            case COMPLETED_WITH_WARNINGS -> "SYNC_WITH_WARNINGS";
            case FAILED -> "JOB_FAILED";
            case RUNNING -> "JOB_RUNNING";
        };
        String title = switch (run.getJobKey()) {
            case TMDB_CATALOG_SYNC -> "TMDB catalog synchronization";
            case COLLECTION_SYNC -> "Collections synchronized";
            case SERIES_TRACKING_SYNC -> "Series tracking completed";
            case SERIES_TRACKING_SCAN -> "Tracked series refresh scheduled";
            case EPISODE_NOTIFICATIONS -> "Episode notifications updated";
            case NOTIFICATION_RETENTION -> "Notification cleanup completed";
            case LETTERBOXD_CLEANUP -> "Letterboxd maintenance completed";
        };
        String description = switch (run.getJobKey()) {
            case TMDB_CATALOG_SYNC -> run.getProcessedCount() + " works checked · "
                    + run.getUpdatedCount() + " refreshes queued";
            case COLLECTION_SYNC -> run.getProcessedCount() + " collections checked · "
                    + run.getUpdatedCount() + " works refreshed";
            case SERIES_TRACKING_SYNC -> run.getProcessedCount() + " series checked";
            case SERIES_TRACKING_SCAN -> run.getProcessedCount() + " series scheduled for refresh";
            case EPISODE_NOTIFICATIONS -> run.getProcessedCount() + " episodes checked";
            case NOTIFICATION_RETENTION -> "Expired activity history was cleaned up";
            case LETTERBOXD_CLEANUP -> "Expired import details were removed";
        };
        if (run.getStatus() == JobRunStatus.FAILED) description = "The scheduled operation could not be completed.";
        return new StatusActivity(type, category, run.getJobKey().name(), title,
                description, run.getFinishedAt(), run.getProcessedCount(), run.getUpdatedCount(), run.getFailureCount());
    }

    private HealthStatus health(JobDefinition definition, BackgroundJobRun latest, Instant now) {
        long failures = runRepository.countRecentFailures(definition.key(), now.minus(FAILURE_WINDOW));
        return StatusHealthPolicy.evaluate(latest == null ? null : latest.getStatus(),
                latest == null ? null : latest.getFinishedAt(), definition.lastScheduledAt(now), now, failures);
    }

    private HealthStatus healthOnDemand(JobKey key, BackgroundJobRun latest, Instant now) {
        long failures = runRepository.countRecentFailures(key, now.minus(FAILURE_WINDOW));
        return StatusHealthPolicy.evaluate(latest == null ? null : latest.getStatus(),
                latest == null ? null : latest.getFinishedAt(), null, now, failures);
    }

    private JobDefinition definition(List<JobDefinition> definitions, JobKey key) {
        return definitions.stream().filter(candidate -> candidate.key() == key).findFirst().orElseThrow();
    }

    private List<JobDefinition> definitions() {
        ZoneId defaultZone = ZoneId.systemDefault();
        return List.of(
                definition(JobKey.TMDB_CATALOG_SYNC, "TMDB catalog", tmdbCron, ZoneId.of(tmdbZone)),
                definition(JobKey.COLLECTION_SYNC, "Collections", collectionsCron, ZoneId.of(collectionsZone)),
                definition(JobKey.SERIES_TRACKING_SCAN, "Series tracking", seriesCron, ZoneId.of(seriesZone)),
                definition(JobKey.EPISODE_NOTIFICATIONS, "Episode notifications", episodeNotificationsCron,
                        ZoneId.of("America/Sao_Paulo")),
                definition(JobKey.NOTIFICATION_RETENTION, "Notification retention", notificationRetentionCron,
                        defaultZone),
                definition(JobKey.LETTERBOXD_CLEANUP, "Letterboxd cleanup", letterboxdCleanupCron, defaultZone));
    }

    private JobDefinition definition(JobKey key, String name, String cron, ZoneId zone) {
        return new JobDefinition(key, name, CronExpression.parse(cron), cron, zone);
    }

    private long durationMillis(BackgroundJobRun run, Instant now) {
        Instant end = run.getFinishedAt() == null ? now : run.getFinishedAt();
        return Math.max(0, Duration.between(run.getStartedAt(), end).toMillis());
    }

    private int value(Map<CatalogOutboxStatus, Integer> counts, CatalogOutboxStatus key) {
        return counts.getOrDefault(key, 0);
    }

    private int safeInt(long value) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, value));
    }

    private HealthStatus worstHealth(List<HealthStatus> states) {
        HealthStatus mostSevere = HealthStatus.OPERATIONAL;
        boolean running = false;
        for (HealthStatus state : states) {
            if (state == HealthStatus.RUNNING) {
                running = true;
            } else if (healthRank(state) > healthRank(mostSevere)) {
                mostSevere = state;
            }
        }
        return mostSevere == HealthStatus.OPERATIONAL && running ? HealthStatus.RUNNING : mostSevere;
    }

    private static HealthStatus moreSevereForOverall(HealthStatus left, HealthStatus right) {
        int leftRank = left == HealthStatus.RUNNING ? 0 : healthRank(left);
        int rightRank = right == HealthStatus.RUNNING ? 0 : healthRank(right);
        return leftRank >= rightRank ? left : right;
    }

    private static int healthRank(HealthStatus health) {
        return switch (health) {
            case OPERATIONAL, RUNNING -> 0;
            case DELAYED -> 1;
            case DEGRADED -> 2;
            case ATTENTION_REQUIRED -> 3;
        };
    }

    private record JobDefinition(JobKey key, String name, CronExpression expression,
                                 String configuredCron, ZoneId zone) {
        Instant nextRunAt(Instant now) {
            ZonedDateTime current = now.atZone(zone);
            ZonedDateTime next = expression.next(current);
            return next == null ? null : next.toInstant();
        }

        Instant lastScheduledAt(Instant now) {
            ZonedDateTime cursor = now.minus(Duration.ofHours(48)).atZone(zone);
            ZonedDateTime last = null;
            for (int i = 0; i < 1000; i++) {
                ZonedDateTime next = expression.next(cursor);
                if (next == null || next.toInstant().isAfter(now)) break;
                last = next;
                cursor = next;
            }
            return last == null ? null : last.toInstant();
        }

        Schedule schedule() {
            String[] fields = configuredCron.trim().split("\\s+");
            if (fields.length != 6) return new Schedule("CUSTOM", null, zone.getId());
            boolean commonCalendar = fields[3].equals("*") && fields[4].equals("*") && fields[5].equals("*");
            if (commonCalendar && numeric(fields[0]) && numeric(fields[1]) && numeric(fields[2])) {
                LocalTime time = LocalTime.of(Integer.parseInt(fields[2]), Integer.parseInt(fields[1]));
                return new Schedule("DAILY", time.format(TIME_FORMAT), zone.getId());
            }
            if (commonCalendar && fields[2].equals("*") && numeric(fields[0]) && numeric(fields[1])) {
                return new Schedule("HOURLY", String.format(":%02d", Integer.parseInt(fields[1])), zone.getId());
            }
            return new Schedule("CUSTOM", null, zone.getId());
        }

        private boolean numeric(String value) {
            return value.matches("\\d+");
        }
    }
}
