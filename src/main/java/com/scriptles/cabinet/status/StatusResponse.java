package com.scriptles.cabinet.status;

import java.time.Instant;
import java.util.List;

public record StatusResponse(
        HealthStatus status,
        Instant updatedAt,
        List<SystemStatus> systems,
        List<SynchronizationStatus> synchronizations,
        BackgroundProcessingStatus backgroundProcessing,
        ImportProcessingStatus importProcessing,
        List<ScheduledJobStatus> scheduledJobs,
        List<StatusActivity> recentActivity
) {
    public record SystemStatus(String key, String name, HealthStatus status, String description) {}

    public record SynchronizationStatus(
            String key,
            String name,
            String description,
            HealthStatus status,
            Instant lastRunAt,
            Instant nextRunAt,
            Long durationMs,
            Integer processed,
            Integer updated,
            Integer failed
    ) {}

    public record BackgroundProcessingStatus(int processing, int waiting, int retrying, int failed) {}

    public record ImportProcessingStatus(int processing, int waiting, int completedLast24Hours) {}

    public record Schedule(String frequency, String localTime, String timeZone) {}

    public record ScheduledJobStatus(
            String key,
            String name,
            Schedule schedule,
            JobRunStatus status,
            Instant lastRunAt,
            Instant nextRunAt,
            Long durationMs
    ) {}

    public record StatusActivity(
            String type,
            String category,
            String key,
            String title,
            String description,
            Instant occurredAt,
            Integer processed,
            Integer updated,
            Integer failed
    ) {}
}
