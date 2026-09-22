package com.scriptles.cabinet.catalog.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "collection_translations", uniqueConstraints =
        @UniqueConstraint(name = "uk_collection_translation_locale", columnNames = {"collection_id", "locale"}))
@Getter @Setter @NoArgsConstructor
public class CollectionTranslation {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "collection_id", nullable = false) private Collection collection;
    @Column(nullable = false, length = 10) private String locale;
    @Column(nullable = false, length = 300) private String title;
    @Column(columnDefinition = "TEXT") private String description;
    @Column(columnDefinition = "TEXT") private String posterUrl;
    @Column(columnDefinition = "TEXT") private String backdropUrl;
    @CreationTimestamp private Instant createdAt;
    @UpdateTimestamp private Instant updatedAt;
}
