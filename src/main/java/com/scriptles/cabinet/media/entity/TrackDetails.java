package com.scriptles.cabinet.media.entity;

import com.scriptles.cabinet.media.enums.AlbumType;
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
@Table(name = "track_details")
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class TrackDetails {
    @Id
    @Column(name = "media_id")
    private UUID id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "media_id", nullable = false)
    private Media media;

    private Integer durationSeconds;

    @Column(nullable = false)
    private Boolean explicit;

    private Integer trackNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "album_media_id")
    private Media album;

    @CreationTimestamp
    private Instant createdAt;
    @UpdateTimestamp
    private Instant updatedAt;
}
