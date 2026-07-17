package com.scriptles.cabinet.media.entity;

import com.scriptles.cabinet.media.enums.ExternalInfoKind;
import com.scriptles.cabinet.media.enums.ExternalInfoSnapshotStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.FetchType;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "external_info_snapshots",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_external_info_snapshot_media_kind_region",
                columnNames = {"media_id", "kind", "region"}
        ),
        indexes = @Index(
                name = "idx_external_info_snapshot_lookup",
                columnList = "media_id, kind, region"
        )
)
@Getter
@Setter
@NoArgsConstructor
public class ExternalInfoSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "media_id", nullable = false)
    private Media media;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ExternalInfoKind kind;

    @Column(nullable = false, length = 8)
    private String region;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ExternalInfoSnapshotStatus status;

    private Instant fetchedAt;
    private Instant expiresAt;

    @Column(length = 80)
    private String errorCode;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
