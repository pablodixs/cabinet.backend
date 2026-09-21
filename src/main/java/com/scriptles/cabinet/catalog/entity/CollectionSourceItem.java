package com.scriptles.cabinet.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "collection_source_items")
@Getter
@Setter
@NoArgsConstructor
public class CollectionSourceItem {
    @Id
    private UUID id;
    @Column(nullable = false)
    private UUID collectionId;
    @Column(nullable = false, length = 30)
    private String provider;
    @Column(nullable = false, length = 255)
    private String externalId;
    @Column(nullable = false, length = 30)
    private String externalMediaType;
    @Column(nullable = false)
    private int position;
    @Column(length = 500)
    private String title;
    @Column(length = 500)
    private String originalTitle;
    private LocalDate releaseDate;
    @Column(columnDefinition = "TEXT")
    private String posterUrl;
    @Column(columnDefinition = "TEXT")
    private String backdropUrl;
    @Column(length = 20)
    private String originalLanguage;
    @Column(length = 64)
    private String contentHash;
    private UUID resolvedMediaId;
    @Column(nullable = false, length = 20)
    private String resolutionStatus;
    @Column(nullable = false)
    private boolean sourcePresent;
    @Column(nullable = false)
    private Instant firstSeenAt;
    @Column(nullable = false)
    private Instant lastSeenAt;
    private Instant removedAt;
    @Column(columnDefinition = "TEXT")
    private String lastMaterializationError;
    private Instant createdAt;
    private Instant updatedAt;
}
