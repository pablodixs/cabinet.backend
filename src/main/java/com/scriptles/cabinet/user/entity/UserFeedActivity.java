package com.scriptles.cabinet.user.entity;

import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.user.enums.FeedActionType;
import com.scriptles.cabinet.user.enums.Visibility;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_feed_activities", uniqueConstraints = @UniqueConstraint(
        name = "uk_user_feed_activity_action", columnNames = {"user_id", "media_id", "action_type"}), indexes = {
        @Index(name = "idx_user_feed_activity_date", columnList = "occurred_at, id")
})
@Getter
@Setter
public class UserFeedActivity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "media_id", nullable = false)
    private Media media;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 30)
    private FeedActionType actionType;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Visibility visibility = Visibility.PUBLIC;

    @Column(precision = 2, scale = 1)
    private BigDecimal rating;

    @Column(columnDefinition = "TEXT")
    private String review;

    @Column(name = "contains_spoilers", nullable = false)
    private boolean containsSpoilers;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
