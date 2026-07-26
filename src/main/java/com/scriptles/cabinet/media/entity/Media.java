package com.scriptles.cabinet.media.entity;

import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.enums.CatalogStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "media",
        indexes = {
                @Index(
                        name = "idx_media_title",
                        columnList = "title"
                ),
                @Index(
                        name = "idx_media_type",
                        columnList = "type"
                )
        })
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Media {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "type", length = 20)
    private String typeValue;

    @Column(length = 300, nullable = false)
    private String title;

    @Column(length = 300)
    private String originalTitle;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 500)
    private String tagline;

    @Column(columnDefinition = "TEXT")
    private String coverUrl;

    @Column(columnDefinition = "TEXT")
    private String backdropUrl;

    @Column(columnDefinition = "TEXT")
    private String logoUrl;

    @Column(length = 30)
    private String wikidataId;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "media_genres", joinColumns = @JoinColumn(name = "media_id"),
            indexes = @Index(name = "idx_media_genres_media_id", columnList = "media_id"))
    @Column(name = "genre", length = 100, nullable = false)
    private Set<String> genres = new LinkedHashSet<>();

    private LocalDate releaseDate;

    @Column(length = 10)
    private String originalLanguage;

    @Column(nullable = false, length = 10)
    private String defaultLocale = "pt-BR";

    @Column(length = 3)
    private String countryCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CatalogStatus catalogStatus = CatalogStatus.READY;

    private Instant coreSyncedAt;

    private Instant enrichmentSyncedAt;

    @Column(nullable = false)
    private int syncVersion;

    @Column(columnDefinition = "TEXT")
    private String lastSyncError;

    @CreationTimestamp
    private Instant createdAt;
    @UpdateTimestamp
    private Instant updatedAt;

    @Version
    @Column(nullable = false, columnDefinition = "bigint default 0")
    private long version;

    public MediaType getType() {
        return typeValue == null ? null : MediaType.valueOf(typeValue);
    }

    public void setType(MediaType type) {
        this.typeValue = type == null ? null : type.name();
    }
}
