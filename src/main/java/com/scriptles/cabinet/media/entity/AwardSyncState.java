package com.scriptles.cabinet.media.entity;

import com.scriptles.cabinet.media.enums.AwardSyncStatus;
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
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Check(name = "ck_award_sync_subject", constraints = """
        (media_id is not null and person_id is null)
        or (media_id is null and person_id is not null)
        """)
@Table(name = "award_sync_states", indexes = {
        @Index(name = "idx_award_sync_media", columnList = "media_id"),
        @Index(name = "idx_award_sync_person", columnList = "person_id")
})
@Getter
@Setter
@NoArgsConstructor
public class AwardSyncState {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_id")
    private Media media;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id")
    private Person person;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AwardSyncStatus status;

    private Instant fetchedAt;
    private Instant expiresAt;

    @Column(length = 80)
    private String errorCode;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
