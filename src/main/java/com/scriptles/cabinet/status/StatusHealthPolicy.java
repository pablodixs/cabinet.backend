package com.scriptles.cabinet.status;

import java.time.Duration;
import java.time.Instant;

public final class StatusHealthPolicy {
    private static final Duration SCHEDULE_GRACE = Duration.ofHours(6);
    private static final Duration DEGRADED_OVERDUE = Duration.ofHours(48);

    private StatusHealthPolicy() {}

    public static HealthStatus evaluate(JobRunStatus latestStatus, Instant latestFinishedAt,
                                        Instant lastScheduledAt, Instant now, long recentFailures) {
        if (recentFailures >= 5) return HealthStatus.ATTENTION_REQUIRED;
        if (recentFailures >= 2) return HealthStatus.DEGRADED;
        if (latestStatus == JobRunStatus.RUNNING) return HealthStatus.RUNNING;
        if (latestStatus == JobRunStatus.COMPLETED_WITH_WARNINGS || latestStatus == JobRunStatus.FAILED) {
            return HealthStatus.DELAYED;
        }
        if (lastScheduledAt == null || now.isBefore(lastScheduledAt.plus(SCHEDULE_GRACE))) {
            return HealthStatus.OPERATIONAL;
        }
        if (latestFinishedAt == null || latestFinishedAt.isBefore(lastScheduledAt)) {
            return now.isAfter(lastScheduledAt.plus(DEGRADED_OVERDUE))
                    ? HealthStatus.DEGRADED
                    : HealthStatus.DELAYED;
        }
        return HealthStatus.OPERATIONAL;
    }
}
