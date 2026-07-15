package com.scriptles.cabinet.media.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "album_tracks", indexes = @Index(name = "idx_album_tracks_album_position", columnList = "album_media_id,disc_number,track_number"))
@Getter
@Setter
@NoArgsConstructor
public class AlbumTrack {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "album_media_id", nullable = false)
    private Media album;

    @Column(length = 200)
    private String externalId;

    @Column(nullable = false, length = 300)
    private String title;

    private Integer discNumber;
    private Integer trackNumber;
    private Integer durationSeconds;
    private Boolean explicit;
}
