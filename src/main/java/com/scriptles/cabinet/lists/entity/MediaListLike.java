package com.scriptles.cabinet.lists.entity;

import com.scriptles.cabinet.user.entity.User;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "media_list_likes", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_media_list_likes_user_list",
                columnNames = {"user_id", "list_id"}
        )
}, indexes = {
        @Index(
                name = "idx_media_list_likes_list_id",
                columnList = "list_id"
        )
})
@Getter
@Setter
public class MediaListLike {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "list_id", nullable = false)
    private MediaList list;

    @CreationTimestamp
    private Instant createdAt;
}
