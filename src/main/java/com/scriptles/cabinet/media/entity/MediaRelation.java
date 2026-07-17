package com.scriptles.cabinet.media.entity;

import com.scriptles.cabinet.media.enums.MediaRelationType;
import com.scriptles.cabinet.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "media_relations", uniqueConstraints = @UniqueConstraint(
        name = "uk_media_relation_direction", columnNames = {"source_media_id", "target_media_id", "relation_type"}
), indexes = {
        @Index(name = "idx_media_relation_source", columnList = "source_media_id"),
        @Index(name = "idx_media_relation_target", columnList = "target_media_id")
})
@Getter
@Setter
@NoArgsConstructor
public class MediaRelation {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_media_id", nullable = false)
    private Media sourceMedia;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_media_id", nullable = false)
    private Media targetMedia;

    @Enumerated(EnumType.STRING)
    @Column(name = "relation_type", nullable = false, length = 30)
    private MediaRelationType relationType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdBy;

    @CreationTimestamp
    private Instant createdAt;
}
