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
@Table(name = "media_likes", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_media_likes_user_media",
                columnNames = {"user_id", "media_id"}
        )
}, indexes = {
        @Index(
                name = "idx_media_likes_media_id",
                columnList = "media_id"
        )
})
@Getter
@Setter
public class MediaLike {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "media_id", nullable = false)
    private Media media;

    @CreationTimestamp
    private Instant createdAt;
}
