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
@Table(name = "external_catalog_entities")
@Getter
@Setter
@NoArgsConstructor
public class ExternalCatalogEntity {
    @Id
    private UUID id;
    @Column(nullable = false, length = 30)
    private String provider;
    @Column(nullable = false, length = 30)
    private String entityType;
    @Column(nullable = false, length = 255)
    private String externalId;
    @Column(length = 500)
    private String displayName;
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> sourceMetadata;
    @Column(nullable = false)
    private Instant firstSeenAt;
    @Column(nullable = false)
    private Instant lastSeenAt;
    private UUID lastIndexRunId;
    @Column(nullable = false, length = 20)
    private String state;
    private Instant removedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
