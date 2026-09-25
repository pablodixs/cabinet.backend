package com.scriptles.cabinet.notifications.entity;

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
@Table(name = "push_installations")
@Getter @Setter @NoArgsConstructor
public class PushInstallation {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(name = "fcm_token", nullable = false, unique = true, columnDefinition = "text")
    private String fcmToken;
    @Column(nullable = false, length = 20)
    private String platform = "IOS";
    @Column(nullable = false)
    private boolean active = true;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt = Instant.now();
}
