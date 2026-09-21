package com.scriptles.cabinet.status;

import com.scriptles.cabinet.status.BackgroundJobTracker.JobRunResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({BackgroundJobTracker.class, BackgroundJobRunner.class})
class BackgroundJobTrackerTest {
    @Autowired BackgroundJobTracker tracker;
    @Autowired BackgroundJobRunner runner;
    @Autowired BackgroundJobRunRepository repository;

    @Test
    void recordsRunningThenCompletedWithCountersAndTimestamps() {
        var id = tracker.start(JobKey.TMDB_CATALOG_SYNC);
        var started = repository.findById(id).orElseThrow();

        assertThat(started.getStatus()).isEqualTo(JobRunStatus.RUNNING);
        assertThat(started.getStartedAt()).isNotNull();
        assertThat(started.getFinishedAt()).isNull();

        var completed = tracker.complete(id, JobRunResult.completed(428, 16, 428, 0, "Safe summary"));

        assertThat(completed.getStatus()).isEqualTo(JobRunStatus.COMPLETED);
        assertThat(completed.getFinishedAt()).isNotNull();
        assertThat(completed.getProcessedCount()).isEqualTo(428);
        assertThat(completed.getUpdatedCount()).isEqualTo(16);
        assertThat(completed.getSuccessCount()).isEqualTo(428);
        assertThat(completed.getFailureCount()).isZero();
    }

    @Test
    void marksPartialSuccessAsCompletedWithWarnings() {
        var completed = runner.execute(JobKey.COLLECTION_SYNC,
                () -> JobRunResult.completed(10, 8, 9, 1, "Some collections need another attempt"));

        assertThat(completed.getStatus()).isEqualTo(JobRunStatus.COMPLETED_WITH_WARNINGS);
        assertThat(completed.getFailureCount()).isEqualTo(1);
        assertThat(completed.getErrorCode()).isEqualTo("PARTIAL_FAILURE");
    }

    @Test
    void persistsFailureWithoutTheExceptionMessage() {
        assertThatThrownBy(() -> runner.execute(JobKey.TMDB_CATALOG_SYNC, () -> {
            throw new IllegalStateException("secret provider response and stack details");
        })).isInstanceOf(IllegalStateException.class);

        var failed = repository.findFirstByJobKeyOrderByStartedAtDesc(JobKey.TMDB_CATALOG_SYNC).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(JobRunStatus.FAILED);
        assertThat(failed.getFailureCount()).isEqualTo(1);
        assertThat(failed.getErrorCode()).isEqualTo("EXECUTION_FAILED");
        assertThat(failed.getSummary()).isNull();
    }
}
