package com.scriptles.cabinet.media.entity;

import com.scriptles.cabinet.media.enums.MediaType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.UUID;

@Entity
@Table(name = "media",
        indexes = {
                @Index(
                        name = "idx_media_title",
                        columnList = "title"
                ),
                @Index(
                        name = "idx_media_type",
                        columnList = "type"
                )
        })
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Media {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    private MediaType type;

    @Column(length = 300, nullable = false)
    private String title;

    @Column(length = 300)
    private String originalTitle;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String coverUrl;

    private LocalDate releaseDate;

    @Column(length = 10)
    private String originalLanguage;

    @Column(length = 3)
    private String countryCode;

    @CreationTimestamp
    private Instant createdAt;
    @UpdateTimestamp
    private Instant updatedAt;
}
