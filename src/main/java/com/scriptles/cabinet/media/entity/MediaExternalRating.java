package com.scriptles.cabinet.media.entity;

import com.scriptles.cabinet.media.enums.ExternalRatingMetric;
import com.scriptles.cabinet.media.enums.ExternalSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

import java.util.UUID;

@Entity
@Table(
        name = "media_external_ratings",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_media_external_rating_provider_metric",
                columnNames = {"media_id", "provider", "metric"}
        ),
        indexes = @Index(name = "idx_media_external_rating_media", columnList = "media_id")
)
@Getter
@Setter
@NoArgsConstructor
public class MediaExternalRating {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "media_id", nullable = false)
    private Media media;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ExternalSource provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ExternalSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ExternalRatingMetric metric;

    @Column(nullable = false)
    private Double value;

    @Column(nullable = false)
    private Integer scale;

    @Column(length = 30)
    private String displayValue;

    @Column(length = 20)
    private String externalId;
}
