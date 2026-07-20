package com.scriptles.cabinet.user.importer;

import com.scriptles.cabinet.media.entity.Media;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "letterboxd_import_items", uniqueConstraints = {
        @UniqueConstraint(name = "uk_letterboxd_import_item_job_source",
                columnNames = {"job_id", "source_key"})
}, indexes = {
        @Index(name = "idx_letterboxd_import_item_job_state", columnList = "job_id, state")
})
@Getter
@Setter
public class LetterboxdImportItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private LetterboxdImportJob job;

    @Column(name = "source_key", nullable = false, length = 700)
    private String sourceKey;

    @Column(name = "letterboxd_uri", length = 700)
    private String letterboxdUri;

    @Column(nullable = false, length = 300)
    private String title;

    private Integer releaseYear;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LetterboxdImportItemState state = LetterboxdImportItemState.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_media_id")
    private Media selectedMedia;

    @Column(length = 100)
    private String selectedTmdbId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String payload;

    @Column(columnDefinition = "TEXT")
    private String matchCandidates;

    @Column(nullable = false)
    private boolean overrideStatus;
    @Column(nullable = false)
    private boolean overrideRating;
    @Column(nullable = false)
    private boolean overrideReview;

    @Column(nullable = false)
    private boolean statusConflict;
    @Column(nullable = false)
    private boolean ratingConflict;
    @Column(nullable = false)
    private boolean reviewConflict;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    private Instant createdAt;
    @UpdateTimestamp
    private Instant updatedAt;
}
