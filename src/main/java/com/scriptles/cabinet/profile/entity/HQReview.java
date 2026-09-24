package com.scriptles.cabinet.profile.entity;

import com.scriptles.cabinet.media.entity.Media;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "hq_reviews", uniqueConstraints = @UniqueConstraint(columnNames = {"hq_profile_id", "media_id"}))
@Getter @Setter @NoArgsConstructor
public class HQReview {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "hq_profile_id", nullable = false) private HQProfile hqProfile;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "media_id", nullable = false) private Media media;
    @Column(nullable = false, columnDefinition = "TEXT") private String content;
    @Column(nullable = false) private boolean containsSpoilers;
    @Column(precision = 2, scale = 1) private BigDecimal rating;
    @CreationTimestamp private Instant createdAt;
    @UpdateTimestamp private Instant updatedAt;
}
