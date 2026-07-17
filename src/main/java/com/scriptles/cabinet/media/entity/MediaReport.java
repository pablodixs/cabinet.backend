package com.scriptles.cabinet.media.entity;

import com.scriptles.cabinet.media.enums.*;
import com.scriptles.cabinet.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "media_reports", indexes = {
        @Index(name = "idx_media_report_status_created", columnList = "status, created_at"),
        @Index(name = "idx_media_report_reporter", columnList = "reported_by_user_id")
})
@Getter
@Setter
@NoArgsConstructor
public class MediaReport {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_id")
    private Media media;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ExternalSource source;

    @Column(name = "external_id", nullable = false, length = 300)
    private String externalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 20)
    private MediaType mediaType;

    @Column(name = "media_title", nullable = false, length = 300)
    private String mediaTitle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private MediaReportCategory category;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "suggested_target_source", length = 30)
    private ExternalSource suggestedTargetSource;

    @Column(name = "suggested_target_external_id", length = 300)
    private String suggestedTargetExternalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "suggested_target_type", length = 20)
    private MediaType suggestedTargetType;

    @Column(name = "suggested_target_title", length = 300)
    private String suggestedTargetTitle;

    @Enumerated(EnumType.STRING)
    @Column(name = "suggested_relation_type", length = 30)
    private MediaRelationType suggestedRelationType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MediaReportStatus status = MediaReportStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reported_by_user_id", nullable = false)
    private User reportedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_user_id")
    private User reviewedBy;

    @Column(name = "resolution_note", columnDefinition = "TEXT")
    private String resolutionNote;

    private Instant resolvedAt;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
