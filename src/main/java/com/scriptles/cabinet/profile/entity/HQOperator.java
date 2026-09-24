package com.scriptles.cabinet.profile.entity;

import com.scriptles.cabinet.profile.enums.HQMemberRole;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "hq_operators", uniqueConstraints = @UniqueConstraint(columnNames = {"hq_profile_id", "email"}))
@Getter @Setter @NoArgsConstructor
public class HQOperator {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "hq_profile_id", nullable = false) private HQProfile hqProfile;
    @Column(nullable = false, length = 254) private String email;
    @Column(nullable = false, length = 120) private String displayName;
    @Column(nullable = false, length = 255) private String passwordHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private HQMemberRole role;
    @Column(nullable = false) private boolean active = true;
    @CreationTimestamp private Instant createdAt;
}
