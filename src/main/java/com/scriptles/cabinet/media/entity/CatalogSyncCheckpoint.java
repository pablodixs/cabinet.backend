package com.scriptles.cabinet.media.entity;

import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "catalog_sync_checkpoint",
        uniqueConstraints = @UniqueConstraint(name = "uk_catalog_sync_checkpoint_source_type",
                columnNames = {"source", "media_type"}))
@Getter
@Setter
@NoArgsConstructor
public class CatalogSyncCheckpoint {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ExternalSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 20)
    private MediaType mediaType;

    private LocalDate lastCompletedEndDate;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @UpdateTimestamp
    private Instant updatedAt;
}
