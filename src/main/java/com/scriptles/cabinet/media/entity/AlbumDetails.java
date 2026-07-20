package com.scriptles.cabinet.media.entity;

import com.scriptles.cabinet.media.enums.AlbumType;
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
@Table(name = "album_details")
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class AlbumDetails {
    @Id
    @Column(name = "media_id")
    private UUID id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "media_id", nullable = false)
    private Media media;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AlbumType albumType;

    private Integer numberOfTracks;

    @Column(columnDefinition = "TEXT")
    private String animatedCoverUrl;

    private LocalDate releaseDate;

    @CreationTimestamp
    private Instant createdAt;
    @UpdateTimestamp
    private Instant updatedAt;
}
