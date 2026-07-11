package com.scriptles.cabinet.media.entity;

import com.scriptles.cabinet.media.enums.ExternalSource;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "external_references",
        uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_external_reference_source_id",
            columnNames = {"source", "external_id"}
        ),
        @UniqueConstraint(
            name = "uk_external_reference_media_source",
            columnNames = {"media_id", "source"}
        )
    })
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ExternalReference {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "media_id", nullable = false)
    private Media media;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExternalSource source;

    @Column(unique = true)
    private String externalId;

    @Column(columnDefinition = "TEXT")
    private String externalUrl;

     @Column(nullable = false)
    private Boolean primaryReference;

    private Instant lastSyncedAt;

    @CreationTimestamp
    private Instant createdAt;
    @UpdateTimestamp
    private Instant updatedAt;
}
