package com.scriptles.cabinet.profile.entity;

import com.scriptles.cabinet.profile.enums.ProfileType;
import com.scriptles.cabinet.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "profiles", uniqueConstraints = @UniqueConstraint(name = "uk_profiles_handle", columnNames = "handle"), indexes = @Index(name = "idx_profiles_user", columnList = "user_id"))
@Getter @Setter @NoArgsConstructor
public class Profile {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ProfileType type;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", unique = true) private User user;
    @Column(nullable = false, length = 80) private String handle;
    @Column(nullable = false, length = 120) private String displayName;
    @Column(length = 500) private String avatarUrl;
    @Column(length = 500) private String backdropUrl;
    @Column(columnDefinition = "TEXT") private String bio;
    @Column(nullable = false) private boolean verified;
    private Instant verifiedAt;
    @Column(nullable = false) private long followersCount;
    @Column(nullable = false) private long followingCount;
    @Column(nullable = false) private boolean active = true;
    @CreationTimestamp private Instant createdAt;
    @UpdateTimestamp private Instant updatedAt;

    public static Profile member(User user) {
        Profile p = new Profile(); p.type = ProfileType.MEMBER; p.user = user; p.handle = user.getUsername();
        p.displayName = user.getDisplayName(); p.bio = user.getBiography(); p.avatarUrl = user.getAvatarUlr(); return p;
    }
}
