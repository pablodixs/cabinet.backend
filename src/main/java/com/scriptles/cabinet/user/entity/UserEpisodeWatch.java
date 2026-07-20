package com.scriptles.cabinet.user.entity;

import com.scriptles.cabinet.media.entity.SeriesEpisode;
import jakarta.persistence.Column;
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
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_episode_watches", uniqueConstraints = @UniqueConstraint(
        name = "uk_user_episode_watches_user_episode",
        columnNames = {"user_id", "series_episode_id"}
), indexes = {
        @Index(name = "idx_user_episode_watches_user_watched", columnList = "user_id, watched_at"),
        @Index(name = "idx_user_episode_watches_episode", columnList = "series_episode_id")
})
@Getter
@Setter
@NoArgsConstructor
public class UserEpisodeWatch {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "series_episode_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private SeriesEpisode episode;

    @Column(nullable = false)
    private Instant watchedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
