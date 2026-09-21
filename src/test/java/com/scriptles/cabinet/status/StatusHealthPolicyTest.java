package com.scriptles.cabinet.status;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class StatusHealthPolicyTest {
    private final Instant now = Instant.parse("2026-09-21T18:00:00Z");

    @Test
    void recentSuccessWithOneRetryRemainsOperational() {
        assertThat(StatusHealthPolicy.evaluate(JobRunStatus.COMPLETED,
                now.minus(30, ChronoUnit.MINUTES), now.minus(1, ChronoUnit.HOURS), now, 1))
                .isEqualTo(HealthStatus.OPERATIONAL);
    }

    @Test
    void aScheduledRunPastTheGracePeriodIsDelayed() {
        assertThat(StatusHealthPolicy.evaluate(null, null,
                now.minus(7, ChronoUnit.HOURS), now, 0))
                .isEqualTo(HealthStatus.DELAYED);
    }

    @Test
    void longOverdueRunIsDegraded() {
        assertThat(StatusHealthPolicy.evaluate(JobRunStatus.COMPLETED,
                now.minus(55, ChronoUnit.HOURS), now.minus(50, ChronoUnit.HOURS), now, 0))
                .isEqualTo(HealthStatus.DEGRADED);
    }

    @Test
    void repeatedFailuresAreDegradedAndManyFailuresNeedAttention() {
        assertThat(StatusHealthPolicy.evaluate(JobRunStatus.FAILED, now.minus(1, ChronoUnit.HOURS),
                now.minus(2, ChronoUnit.HOURS), now, 2)).isEqualTo(HealthStatus.DEGRADED);
        assertThat(StatusHealthPolicy.evaluate(JobRunStatus.FAILED, now.minus(1, ChronoUnit.HOURS),
                now.minus(2, ChronoUnit.HOURS), now, 5)).isEqualTo(HealthStatus.ATTENTION_REQUIRED);
    }

    @Test
    void aSingleFailedRunAndAnActiveSyncDoNotMarkTheWholeProductDown() {
        assertThat(StatusHealthPolicy.evaluate(JobRunStatus.FAILED, now.minus(1, ChronoUnit.MINUTES),
                now.minus(2, ChronoUnit.HOURS), now, 1)).isEqualTo(HealthStatus.DELAYED);
        assertThat(StatusHealthPolicy.evaluate(JobRunStatus.RUNNING, null,
                now.minus(1, ChronoUnit.HOURS), now, 0)).isEqualTo(HealthStatus.RUNNING);
    }
}
