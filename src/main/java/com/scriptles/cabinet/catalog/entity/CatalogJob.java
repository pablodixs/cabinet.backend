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
@Table(name = "catalog_jobs")
@Getter
@Setter
@NoArgsConstructor
public class CatalogJob {
    @Id
    private UUID id;
    private UUID operationId;
    @Column(nullable = false, length = 60)
    private String jobType;
    @Column(length = 30)
    private String provider;
    @Column(length = 30)
    private String entityType;
    @Column(length = 255)
    private String externalId;
    private UUID collectionId;
    private UUID mediaId;
    @Column(nullable = false)
    private int priority;
    @Column(nullable = false, length = 40)
    private String trigger;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(length = 300)
    private String deduplicationKey;
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> payload;
    @Column(nullable = false)
    private int attempts;
    @Column(nullable = false)
    private Instant availableAt;
    private Instant lockedAt;
    @Column(length = 160)
    private String lockedBy;
    private Instant startedAt;
    private Instant completedAt;
    @Column(columnDefinition = "TEXT")
    private String lastError;
    private Instant createdAt;
    private Instant updatedAt;
}
