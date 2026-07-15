package com.scriptles.cabinet.lists.entity;

import com.scriptles.cabinet.media.entity.Media;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "media_list_items", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_media_list_items_list_media",
                columnNames = {"list_id", "media_id"}
        )
}, indexes = {
        @Index(
            name = "idx_list_items_position",
            columnList = "list_id, position"
        ),
        @Index(
            name = "idx_media_list_items_media_id",
            columnList = "media_id"
        )
    })
@Getter
@Setter
public class MediaListItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "list_id",
        nullable = false
    )
    private MediaList list;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "media_id",
        nullable = false
    )
    private Media media;

    @Column(nullable = false)
    private Integer position;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    private Instant createdAt;
}
