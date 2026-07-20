package com.scriptles.cabinet.user.importer;

import com.scriptles.cabinet.user.entity.User;
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
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "letterboxd_import_jobs", indexes = {
        @Index(name = "idx_letterboxd_import_job_user_state", columnList = "user_id, state")
})
@Getter
@Setter
public class LetterboxdImportJob {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private LetterboxdImportJobState state = LetterboxdImportJobState.PARSING;

    @Column(nullable = false)
    private int totalItems;
    @Column(nullable = false)
    private int matchedItems;
    @Column(nullable = false)
    private int reviewItems;
    @Column(nullable = false)
    private int importedItems;
    @Column(nullable = false)
    private int preservedItems;
    @Column(nullable = false)
    private int skippedItems;
    @Column(nullable = false)
    private int failedItems;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    private Instant expiresAt;
    private Instant completedAt;

    @CreationTimestamp
    private Instant createdAt;
    @UpdateTimestamp
    private Instant updatedAt;
}
