package com.scriptles.cabinet.user.entity;

import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.ArtworkProvider;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_media_artwork_preferences", uniqueConstraints = @UniqueConstraint(
        name = "uk_user_media_artwork_preference", columnNames = {"user_id", "media_id"}
), indexes = @Index(
        name = "idx_user_media_artwork_preference_user_media", columnList = "user_id, media_id"
))
@Getter
@Setter
@NoArgsConstructor
public class UserMediaArtworkPreference {
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
    @Column(name = "cover_provider", length = 40)
    private ArtworkProvider coverProvider;

    @Column(name = "cover_key", columnDefinition = "TEXT")
    private String coverKey;

    @Column(name = "cover_url", columnDefinition = "TEXT")
    private String coverUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "backdrop_provider", length = 40)
    private ArtworkProvider backdropProvider;

    @Column(name = "backdrop_key", columnDefinition = "TEXT")
    private String backdropKey;

    @Column(name = "backdrop_url", columnDefinition = "TEXT")
    private String backdropUrl;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
