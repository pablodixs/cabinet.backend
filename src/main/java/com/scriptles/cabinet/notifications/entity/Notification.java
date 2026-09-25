package com.scriptles.cabinet.notifications.entity;

import com.scriptles.cabinet.comments.entity.Comment;
import com.scriptles.cabinet.lists.entity.MediaList;
import com.scriptles.cabinet.media.entity.MediaReport;
import com.scriptles.cabinet.media.entity.Review;
import com.scriptles.cabinet.media.entity.SeriesEpisode;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.notifications.enums.NotificationType;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.importer.LetterboxdImportJob;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notifications_recipient_updated", columnList = "recipient_id, activity_at"),
        @Index(name = "idx_notifications_recipient_unread", columnList = "recipient_id, read_at, activity_at")
})
@Getter
@Setter
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User recipient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationType type;

    @Column(nullable = false)
    private long actorCount = 1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_list_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private MediaList mediaList;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Review review;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comment_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Comment comment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private MediaReport report;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "series_episode_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private SeriesEpisode seriesEpisode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "letterboxd_import_job_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private LetterboxdImportJob letterboxdImportJob;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Media media;

    private Instant readAt;

    @Column(nullable = false)
    private Instant activityAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
