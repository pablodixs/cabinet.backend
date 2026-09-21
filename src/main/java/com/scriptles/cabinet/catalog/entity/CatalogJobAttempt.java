package com.scriptles.cabinet.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "catalog_job_attempts")
@Getter
@Setter
@NoArgsConstructor
public class CatalogJobAttempt {
    @Id
    private UUID id;
    @Column(nullable = false)
    private UUID jobId;
    @Column(nullable = false)
    private int attemptNumber;
    @Column(nullable = false, length = 160)
    private String workerId;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(nullable = false)
    private Instant startedAt;
    private Instant finishedAt;
    private Long durationMs;
    @Column(length = 300)
    private String errorClass;
    @Column(columnDefinition = "TEXT")
    private String errorMessage;
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> metrics;
}
