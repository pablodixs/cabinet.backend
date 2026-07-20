package com.scriptles.cabinet.user.entity;

import com.scriptles.cabinet.user.enums.AccountTier;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_account_tier_changes", indexes = {
        @Index(name = "idx_user_account_tier_change_target", columnList = "target_user_id, created_at"),
        @Index(name = "idx_user_account_tier_change_actor", columnList = "changed_by_user_id, created_at")
})
@Getter
@Setter
@NoArgsConstructor
public class UserAccountTierChange {
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
    @Column(name = "previous_tier", nullable = false, length = 20)
    private AccountTier previousTier;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_tier", nullable = false, length = 20)
    private AccountTier newTier;

    @CreationTimestamp
    private Instant createdAt;
}
