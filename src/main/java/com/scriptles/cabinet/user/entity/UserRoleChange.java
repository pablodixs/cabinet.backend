package com.scriptles.cabinet.user.entity;

import com.scriptles.cabinet.user.enums.UserRole;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_role_changes", indexes = {
        @Index(name = "idx_user_role_change_target", columnList = "target_user_id, created_at"),
        @Index(name = "idx_user_role_change_actor", columnList = "changed_by_user_id, created_at")
})
@Getter
@Setter
@NoArgsConstructor
public class UserRoleChange {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_user_id", nullable = false)
    private User targetUser;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "changed_by_user_id", nullable = false)
    private User changedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_role", nullable = false, length = 20)
    private UserRole previousRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_role", nullable = false, length = 20)
    private UserRole newRole;

    @CreationTimestamp
    private Instant createdAt;
}
