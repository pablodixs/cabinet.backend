package com.scriptles.cabinet.user.entity;

import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.user.enums.ProgressUnit;
import com.scriptles.cabinet.user.enums.UserMediaStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_media", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_user_media_user_media",
                columnNames = {"user_id", "media_id"}
        )
}, indexes = {
        @Index(
            name = "idx_user_media_user_status",
            columnList = "user_id, status"
        ),
        @Index(
            name = "idx_user_media_media_id",
            columnList = "media_id"
        )
    })
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UserMedia {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "media_id", nullable = false)
    private Media media;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserMediaStatus status = UserMediaStatus.PLANNED;

    private Integer progress;

    private ProgressUnit progressUnit;

    private Instant startedAt;

    private Instant completedAt;

    private Instant lastInteractionAt;

    @Column(nullable = false)
    private Boolean favorite = false;

    @Column(nullable = false)
    private Boolean privateEntry = false;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
