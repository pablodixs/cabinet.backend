package com.scriptles.cabinet.media.entity;

import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.TranslationStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "media_translations", uniqueConstraints = @UniqueConstraint(
        name = "uk_media_translation_locale", columnNames = {"media_id", "locale"}))
@Getter
@Setter
@NoArgsConstructor
public class MediaTranslation {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "media_id", nullable = false)
    private Media media;

    @Column(nullable = false, length = 10)
    private String locale;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 500)
    private String tagline;

    @Column(length = 2000)
    private String coverUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ExternalSource source;

    @Column(name = "source_language", length = 10)
    private String originalLanguage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TranslationStatus translationStatus;

    private Instant lastSyncedAt;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    /**
     * Compatibility accessors for callers that still use the database column's
     * former domain name.
     */
    @Deprecated(forRemoval = false)
    public String getSourceLanguage() {
        return originalLanguage;
    }

    @Deprecated(forRemoval = false)
    public void setSourceLanguage(String sourceLanguage) {
        this.originalLanguage = sourceLanguage;
    }
}
