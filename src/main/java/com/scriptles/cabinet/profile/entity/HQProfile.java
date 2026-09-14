package com.scriptles.cabinet.profile.entity;

import com.scriptles.cabinet.profile.enums.*;
import jakarta.persistence.*;
import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp; import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant; import java.util.UUID;

@Entity @Table(name="hq_profiles", uniqueConstraints=@UniqueConstraint(name="uk_hq_profile_profile", columnNames="profile_id"))
@Getter @Setter @NoArgsConstructor
public class HQProfile {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @OneToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="profile_id", nullable=false) private Profile profile;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=40) private HQType hqType;
    @Column(length=160) private String legalName;
    @Column(length=500) private String websiteUrl;
    @Column(length=254) private String contactEmail;
    @Column(length=2) private String countryCode;
    private Integer foundedYear;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=30) private HQClaimStatus claimStatus = HQClaimStatus.UNCLAIMED;
    @Column(length=30) private String externalSource;
    @Column(length=255) private String externalId;
    private Instant lastSyncedAt;
    @CreationTimestamp private Instant createdAt;
    @UpdateTimestamp private Instant updatedAt;
}
