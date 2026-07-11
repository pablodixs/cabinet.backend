package com.scriptles.cabinet.user.entity;

import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.user.enums.ProgressUnit;
import com.scriptles.cabinet.user.enums.UserMediaStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_media", indexes = {
        @Index(
            name = "idx_user_media_user_status",
            columnList = "user_id, status"
        )
    })
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
    private UserMediaStatus status = UserMediaStatus.PLANNED;

    private Integer progress;

    private ProgressUnit progressUnit;

    private Instant startedAt;

    private Instant completedAt;

    private Instant lastInteractionAt;

    @Column(nullable = false)
    private Boolean favorite;

    @Column(nullable = false)
    private Boolean privateEntry;
}
