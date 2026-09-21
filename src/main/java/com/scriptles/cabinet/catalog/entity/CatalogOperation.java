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
@Table(name = "catalog_operations")
@Getter
@Setter
@NoArgsConstructor
public class CatalogOperation {
    @Id
    private UUID id;
    @Column(nullable = false, length = 50)
    private String type;
    @Column(length = 30)
    private String provider;
    @Column(nullable = false, length = 40)
    private String trigger;
    @Column(nullable = false, length = 32)
    private String status;
    private UUID requestedBy;
    @Column(length = 30)
    private String rootEntityType;
    @Column(length = 255)
    private String rootExternalId;
    private UUID rootCollectionId;
    @Column(nullable = false)
    private long totalItems;
    @Column(nullable = false)
    private long processedItems;
    @Column(nullable = false)
    private long createdItems;
    @Column(nullable = false)
    private long updatedItems;
    @Column(nullable = false)
    private long unchangedItems;
    @Column(nullable = false)
    private long failedItems;
    private Instant startedAt;
    private Instant completedAt;
    @Column(columnDefinition = "TEXT")
    private String lastError;
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> metadata;
    private Instant createdAt;
    private Instant updatedAt;
}
