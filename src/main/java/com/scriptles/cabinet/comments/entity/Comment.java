package com.scriptles.cabinet.comments.entity;

import com.scriptles.cabinet.lists.entity.MediaList;
import com.scriptles.cabinet.media.entity.Review;
import com.scriptles.cabinet.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "comments", indexes = {
        @Index(name = "idx_comments_list_root_created", columnList = "media_list_id, created_at"),
        @Index(name = "idx_comments_review_root_created", columnList = "review_id, created_at"),
        @Index(name = "idx_comments_parent_created", columnList = "parent_id, created_at")
})
@Getter
@Setter
public class Comment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User author;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_list_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private MediaList mediaList;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Review review;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Comment parent;

    @Column(length = 2000)
    private String content;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    private Instant deletedAt;
}
