package com.scriptles.cabinet.user.entity;

import com.scriptles.cabinet.media.entity.Media;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_profile_favorites", uniqueConstraints = {
        @UniqueConstraint(name = "uk_profile_favorite_user_media",
                columnNames = {"user_id", "media_id"}),
        @UniqueConstraint(name = "uk_profile_favorite_user_position",
                columnNames = {"user_id", "position"})
})
@Getter
@Setter
@NoArgsConstructor
public class UserProfileFavorite {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "media_id", nullable = false)
    private Media media;

    @Column(nullable = false)
    private int position;

    @CreationTimestamp
    private Instant createdAt;
}
