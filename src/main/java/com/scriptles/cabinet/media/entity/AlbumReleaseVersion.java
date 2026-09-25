package com.scriptles.cabinet.media.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "album_release_versions", uniqueConstraints = @UniqueConstraint(
        name = "uk_album_release_versions_musicbrainz_release",
        columnNames = "musicbrainz_release_id"), indexes = {
        @Index(name = "idx_album_release_versions_album", columnList = "album_media_id"),
        @Index(name = "idx_album_release_versions_album_cursor", columnList = "album_media_id,id"),
        @Index(name = "idx_album_release_versions_barcode", columnList = "barcode")
})
@Getter
@Setter
@NoArgsConstructor
public class AlbumReleaseVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "album_media_id", nullable = false)
    private Media album;

    @Column(name = "musicbrainz_release_id", nullable = false, length = 36)
    private UUID musicBrainzReleaseId;

    @Column(length = 300)
    private String title;

    @Column(length = 3)
    private String countryCode;

    private LocalDate releaseDate;

    @Column(length = 200)
    private String format;

    @Column(length = 40)
    private String status;

    @Column(length = 80)
    private String barcode;

    @Column(length = 200)
    private String catalogNumber;

    @Column(length = 300)
    private String labelName;

    @Column(columnDefinition = "TEXT")
    private String coverUrl;

    private Integer trackCount;

    @Column(name = "is_primary", nullable = false)
    private boolean isPrimary;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    private Instant lastSyncedAt;
}
