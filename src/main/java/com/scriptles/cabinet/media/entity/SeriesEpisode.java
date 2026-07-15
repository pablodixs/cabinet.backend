package com.scriptles.cabinet.media.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "series_episodes", uniqueConstraints = @UniqueConstraint(name = "uk_series_episode_number", columnNames = {"season_id", "episode_number"}),
        indexes = @Index(name = "idx_series_episodes_season", columnList = "season_id"))
@Getter
@Setter
@NoArgsConstructor
public class SeriesEpisode {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "season_id", nullable = false)
    private SeriesSeason season;

    @Column(length = 100)
    private String externalId;
    private Integer episodeNumber;
    @Column(nullable = false, length = 300)
    private String title;
    @Column(columnDefinition = "TEXT")
    private String description;
    @Column(columnDefinition = "TEXT")
    private String stillUrl;
    private LocalDate airDate;
    private Integer runtimeMinutes;
}
