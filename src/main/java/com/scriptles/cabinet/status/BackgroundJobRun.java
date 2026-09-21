package com.scriptles.cabinet.status;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "background_job_runs")
@Getter
@Setter
@NoArgsConstructor
public class BackgroundJobRun {
    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_key", nullable = false, length = 50)
    private JobKey jobKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private JobRunStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "processed_count", nullable = false)
    private int processedCount;

    @Column(name = "updated_count", nullable = false)
    private int updatedCount;

    @Column(name = "success_count", nullable = false)
    private int successCount;

    @Column(name = "failure_count", nullable = false)
    private int failureCount;

    @Column(length = 300)
    private String summary;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prepareForInsert() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }
}
