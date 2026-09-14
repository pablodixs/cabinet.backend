package com.scriptles.cabinet.profile.entity;

import com.scriptles.cabinet.profile.enums.OrganizationType;
import jakarta.persistence.*; import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter; import org.hibernate.annotations.CreationTimestamp; import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant; import java.util.UUID;

@Entity @Table(name="catalog_organizations", uniqueConstraints=@UniqueConstraint(name="uk_catalog_org_source_id", columnNames={"primary_source","primary_external_id"}))
@Getter @Setter @NoArgsConstructor
public class CatalogOrganization {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=40) private OrganizationType type;
 @Column(nullable=false,length=200) private String canonicalName;
 @Column(columnDefinition="TEXT") private String aliases;
 @Column(length=2) private String countryCode;
 @Column(length=500) private String websiteUrl;
 @Column(length=500) private String logoUrl;
 @Column(columnDefinition="TEXT") private String description;
 @Column(nullable=false,length=30) private String primarySource;
 @Column(nullable=false,length=255) private String primaryExternalId;
 private Instant lastSyncedAt;
 @CreationTimestamp private Instant createdAt; @UpdateTimestamp private Instant updatedAt;
}
