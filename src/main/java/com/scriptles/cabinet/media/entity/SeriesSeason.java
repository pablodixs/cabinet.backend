package com.scriptles.cabinet.media.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "series_seasons", uniqueConstraints = @UniqueConstraint(name = "uk_series_season_number", columnNames = {"series_media_id", "season_number"}),
        indexes = @Index(name = "idx_series_seasons_series", columnList = "series_media_id"))
@Getter
@Setter
@NoArgsConstructor
public class SeriesSeason {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "series_media_id", nullable = false)
    private Media series;

    @Column(length = 100)
    private String externalId;
    private Integer seasonNumber;
    @Column(length = 300)
    private String name;
    @Column(columnDefinition = "TEXT")
    private String description;
    @Column(columnDefinition = "TEXT")
    private String coverUrl;
    private Integer episodeCount;
    private LocalDate airDate;
}
