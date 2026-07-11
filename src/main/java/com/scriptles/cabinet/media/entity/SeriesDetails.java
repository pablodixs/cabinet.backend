package com.scriptles.cabinet.media.entity;

import com.scriptles.cabinet.media.enums.SeriesStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "series_details")
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class SeriesDetails {
    @Id
    @Column(name = "media_id")
    private UUID id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "media_id", nullable = false)
    private Media media;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private SeriesStatus status;

    private Integer numberOfSeasons;

    private Integer numberOfEpisodes;

    private LocalDate firstAirDate;

    private LocalDate lastAirDate;

    @CreationTimestamp
    private Instant createdAt;
    @UpdateTimestamp
    private Instant updatedAt;
}
