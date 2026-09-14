package com.scriptles.cabinet.catalog.entity;

import com.scriptles.cabinet.catalog.domain.CatalogEntityStatus;
import com.scriptles.cabinet.catalog.franchise.FranchiseType;
import jakarta.persistence.*;
import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp; import org.hibernate.annotations.UpdateTimestamp;
import java.time.*; import java.util.UUID;

@Entity @Table(name="franchises", indexes=@Index(name="idx_franchises_parent", columnList="parent_id"))
@Getter @Setter @NoArgsConstructor
public class Franchise {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @Column(nullable=false, unique=true, length=180) private String slug;
 @Column(nullable=false, length=300) private String name;
 @Column(length=300) private String originalName;
 @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private FranchiseType type;
 @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private CatalogEntityStatus status=CatalogEntityStatus.ACTIVE;
 @Column(columnDefinition="TEXT") private String description;
 @Column(columnDefinition="TEXT") private String posterUrl;
 @Column(columnDefinition="TEXT") private String backdropUrl;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="parent_id") private Franchise parent;
 private LocalDate startDate; private LocalDate endDate;
 @CreationTimestamp private Instant createdAt; @UpdateTimestamp private Instant updatedAt;
}
