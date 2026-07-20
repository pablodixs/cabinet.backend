package com.scriptles.cabinet.media.entity;

import com.scriptles.cabinet.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "award_entry_revisions", indexes = {
        @Index(name = "idx_award_revision_entry", columnList = "award_entry_id, created_at"),
        @Index(name = "idx_award_revision_editor", columnList = "edited_by_user_id, created_at")
})
@Getter
@Setter
@NoArgsConstructor
public class AwardEntryRevision {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "award_entry_id", nullable = false)
    private AwardEntry awardEntry;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "edited_by_user_id", nullable = false)
    private User editedBy;

    @Column(nullable = false, length = 30)
    private String action;

    @Column(name = "before_state", columnDefinition = "TEXT")
    private String beforeState;

    @Column(name = "after_state", columnDefinition = "TEXT")
    private String afterState;

    @CreationTimestamp
    private Instant createdAt;
}
