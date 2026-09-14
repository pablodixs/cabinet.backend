package com.scriptles.cabinet.profile.entity;
import jakarta.persistence.*; import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter; import java.util.UUID;
@Entity @Table(name="hq_catalog_links",uniqueConstraints=@UniqueConstraint(name="uk_hq_catalog_link",columnNames={"hq_profile_id","organization_id"})) @Getter @Setter @NoArgsConstructor
public class HQCatalogLink { @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id; @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="hq_profile_id",nullable=false) private HQProfile hqProfile; @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="organization_id",nullable=false) private CatalogOrganization organization; }
