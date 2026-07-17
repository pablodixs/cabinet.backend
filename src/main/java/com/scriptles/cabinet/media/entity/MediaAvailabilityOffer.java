package com.scriptles.cabinet.media.entity;

import com.scriptles.cabinet.media.enums.ExternalOfferType;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(
        name = "media_availability_offers",
        indexes = @Index(
                name = "idx_media_availability_offer_lookup",
                columnList = "media_id, country_code"
        )
)
@Getter
@Setter
@NoArgsConstructor
public class MediaAvailabilityOffer {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "media_id", nullable = false)
    private Media media;

    @Column(nullable = false, length = 2)
    private String countryCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ExternalSource dataSource;

    @Column(length = 255)
    private String providerId;

    @Column(nullable = false, length = 150)
    private String providerName;

    @Column(columnDefinition = "TEXT")
    private String logoUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ExternalOfferType offerType;

    @Column(columnDefinition = "TEXT")
    private String externalUrl;

    @Column(columnDefinition = "TEXT")
    private String sourceUrl;

    private Integer displayPriority;
}
