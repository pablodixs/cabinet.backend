package com.scriptles.cabinet.user.entity;

import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.user.enums.ProfileActivityType;
import com.scriptles.cabinet.user.enums.Visibility;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "user_media_activities", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_media_activity_source_key",
                columnNames = {"user_id", "source", "source_key"})
}, indexes = {
        @Index(name = "idx_user_media_activity_user_date", columnList = "user_id, occurred_on"),
        @Index(name = "idx_user_media_activity_media", columnList = "media_id")
})
@Getter
@Setter
public class UserMediaActivity {
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
    @Column(nullable = false, length = 30)
    private ProfileActivityType type;

    @Column(name = "occurred_on", nullable = false)
    private LocalDate occurredOn;

    @Column(name = "logged_on")
    private LocalDate loggedOn;

    @Column(precision = 2, scale = 1)
    private BigDecimal rating;

    @Column(name = "review_content", columnDefinition = "TEXT")
    private String reviewContent;

    @Column(name = "contains_spoilers", nullable = false)
    private Boolean containsSpoilers = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Visibility visibility = Visibility.PUBLIC;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private ExternalSource source;

    @Column(name = "source_key", length = 700)
    private String sourceKey;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "user_media_activity_tags",
            joinColumns = @JoinColumn(name = "activity_id"))
    @Column(name = "tag", nullable = false, length = 100)
    private Set<String> tags = new LinkedHashSet<>();

    @CreationTimestamp
    private Instant createdAt;
}
