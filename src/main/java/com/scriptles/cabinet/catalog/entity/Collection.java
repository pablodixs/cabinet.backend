package com.scriptles.cabinet.catalog.entity;

import com.scriptles.cabinet.catalog.domain.CatalogEntityStatus; import com.scriptles.cabinet.catalog.collection.*;
import jakarta.persistence.*; import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp; import org.hibernate.annotations.UpdateTimestamp; import java.time.*; import java.util.UUID;

@Entity @Table(name="collections") @Getter @Setter @NoArgsConstructor
public class Collection {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @Column(nullable=false, unique=true, length=180) private String slug;
 @Column(nullable=false, length=300) private String title;
 @Column(nullable=false, length=10) private String defaultLocale="pt-BR";
 @Column(length=300) private String originalTitle;
 @Column(columnDefinition="TEXT") private String description;
 @Enumerated(EnumType.STRING) @Column(nullable=false, length=30) private CollectionType type;
 @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private CollectionSourceMode sourceMode;
 @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private CatalogEntityStatus status=CatalogEntityStatus.ACTIVE;
 @Column(columnDefinition="TEXT") private String posterUrl; @Column(columnDefinition="TEXT") private String backdropUrl;
 private LocalDate startDate; private LocalDate endDate; private Instant lastSyncedAt;
 @CreationTimestamp private Instant createdAt; @UpdateTimestamp private Instant updatedAt;
}
