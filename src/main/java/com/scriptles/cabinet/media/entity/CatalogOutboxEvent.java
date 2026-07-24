package com.scriptles.cabinet.media.entity;

import com.scriptles.cabinet.media.enums.CatalogEventType;
import com.scriptles.cabinet.media.enums.CatalogOutboxStatus;
import com.scriptles.cabinet.media.catalog.CatalogEventPayload;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "catalog_outbox", indexes = @Index(
        name = "idx_catalog_outbox_claim", columnList = "status, available_at, created_at"))
@Getter
@Setter
@NoArgsConstructor
public class CatalogOutboxEvent {
    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID aggregateId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 80)
    private CatalogEventType eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private CatalogEventPayload payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CatalogOutboxStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private Instant availableAt;

    private Instant lockedAt;

    @Column(length = 120)
    private String lockedBy;

    @Column(columnDefinition = "TEXT")
    private String lastError;

    @CreationTimestamp
    private Instant createdAt;

    private Instant processedAt;
}
