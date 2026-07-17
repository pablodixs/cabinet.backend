package com.scriptles.cabinet.media.entity;

import com.scriptles.cabinet.user.entity.User;
import jakarta.persistence.Entity;
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

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "review_likes", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_review_likes_user_review",
                columnNames = {"user_id", "review_id"}
        )
}, indexes = {
        @Index(
                name = "idx_review_likes_review_id",
                columnList = "review_id"
        )
})
@Getter
@Setter
public class ReviewLike {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_id", nullable = false)
    private Review review;

    @CreationTimestamp
    private Instant createdAt;
}
