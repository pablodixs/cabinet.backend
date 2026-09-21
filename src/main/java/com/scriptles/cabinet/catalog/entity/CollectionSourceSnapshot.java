package com.scriptles.cabinet.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "collection_source_snapshots")
@Getter
@Setter
@NoArgsConstructor
public class CollectionSourceSnapshot {
    @Id
    private UUID id;
    @Column(nullable = false)
    private UUID collectionId;
    @Column(nullable = false, length = 30)
    private String provider;
    @Column(nullable = false, length = 255)
    private String externalId;
    @Column(nullable = false, length = 64)
    private String contentHash;
    @Column(length = 500)
    private String name;
    @Column(columnDefinition = "TEXT")
    private String overview;
    @Column(columnDefinition = "TEXT")
    private String posterUrl;
    @Column(columnDefinition = "TEXT")
    private String backdropUrl;
    @Column(nullable = false)
    private int remoteItemCount;
    @Column(nullable = false)
    private Instant fetchedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
