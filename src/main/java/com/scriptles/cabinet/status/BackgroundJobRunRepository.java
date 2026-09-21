package com.scriptles.cabinet.status;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BackgroundJobRunRepository extends JpaRepository<BackgroundJobRun, UUID> {
    Optional<BackgroundJobRun> findFirstByJobKeyOrderByStartedAtDesc(JobKey jobKey);

    List<BackgroundJobRun> findAllByJobKeyAndStartedAtAfter(JobKey jobKey, Instant since);

    List<BackgroundJobRun> findAllByFinishedAtIsNotNullOrderByFinishedAtDesc(Pageable pageable);

    @Query("select count(run) from BackgroundJobRun run " +
            "where run.jobKey = :jobKey and run.status = com.scriptles.cabinet.status.JobRunStatus.FAILED " +
            "and run.startedAt >= :since")
    long countRecentFailures(@Param("jobKey") JobKey jobKey, @Param("since") Instant since);

    @Modifying
    @Query("delete from BackgroundJobRun run where run.createdAt < :cutoff")
    int deleteExpired(@Param("cutoff") Instant cutoff);
}
