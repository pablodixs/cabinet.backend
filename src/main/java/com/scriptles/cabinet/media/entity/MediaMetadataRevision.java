package com.scriptles.cabinet.media.entity;

import com.scriptles.cabinet.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "media_metadata_revisions", indexes = {
        @Index(name = "idx_media_revision_media", columnList = "media_id, created_at"),
        @Index(name = "idx_media_revision_editor", columnList = "edited_by_user_id, created_at")
})
@Getter
@Setter
@NoArgsConstructor
public class MediaMetadataRevision {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "media_id", nullable = false)
    private Media media;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "edited_by_user_id", nullable = false)
    private User editedBy;

    @Column(name = "before_state", nullable = false, columnDefinition = "TEXT")
    private String beforeState;

    @Column(name = "after_state", nullable = false, columnDefinition = "TEXT")
    private String afterState;

    @CreationTimestamp
    private Instant createdAt;
}
