package com.scriptles.cabinet.media.entity;

import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.entity.UserMediaActivity;
import com.scriptles.cabinet.user.enums.Visibility;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reviews", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_reviews_user_media",
                columnNames = {"user_id", "media_id"}
        ),
        @UniqueConstraint(
                name = "uk_reviews_activity",
                columnNames = {"activity_id"}
        )
}, indexes = {
        @Index(
                name = "idx_reviews_created",
                columnList = "created_at"
        )
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Review {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "media_id", nullable = false)
    private Media media;

    @OneToOne(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinColumn(name = "rating_id", unique = true)
    private Rating rating;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id", unique = true)
    private UserMediaActivity activity;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "backdrop_key", length = 500)
    private String backdropKey;

    @Column(name = "backdrop_url", columnDefinition = "TEXT")
    private String backdropUrl;

    @Column(nullable = false)
    private Boolean containsSpoilers = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Visibility visibility = Visibility.PUBLIC;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    private Instant publishedAt;

    @PrePersist
    void initializePublishedAt() {
        if (publishedAt == null) publishedAt = Instant.now();
    }

    public Rating getRatingEntity() {
        return rating;
    }

    public void setRatingEntity(Rating rating) {
        this.rating = rating;
    }

    public java.math.BigDecimal getRating() {
        return rating == null ? null : rating.getValue();
    }

    public void setRating(java.math.BigDecimal value) {
        ensureRating().setValue(value);
    }

    private Rating ensureRating() {
        if (rating == null) {
            rating = new Rating();
        }
        return rating;
    }
}
