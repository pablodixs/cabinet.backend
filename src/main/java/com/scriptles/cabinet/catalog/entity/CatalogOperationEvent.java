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
@Table(name = "catalog_operation_events")
@Getter
@Setter
@NoArgsConstructor
public class CatalogOperationEvent {
    @Id
    private UUID id;
    @Column(nullable = false)
    private UUID operationId;
    private UUID jobId;
    @Column(nullable = false, length = 60)
    private String eventType;
    @Column(nullable = false, length = 12)
    private String severity;
    @Column(length = 30)
    private String entityType;
    private UUID entityId;
    @Column(length = 255)
    private String externalId;
    @Column(nullable = false, length = 1000)
    private String message;
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> metadata;
    @Column(nullable = false)
    private Instant occurredAt;
}
