package com.scriptles.cabinet.lists.entity;

import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.enums.Visibility;
import com.scriptles.cabinet.media.enums.ExternalSource;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "media_lists")
@Getter
@Setter
public class MediaList {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "owner_id",
        nullable = false
    )
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hq_profile_id")
    private com.scriptles.cabinet.profile.entity.HQProfile hqProfile;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "rich_description", columnDefinition = "TEXT")
    private String richDescription;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Visibility visibility = Visibility.PUBLIC;

    @Column(nullable = false)
    private boolean ordered = true;

    @Column(length = 500, columnDefinition = "TEXT")
    private String coverUrl;

    @Column(length = 500, columnDefinition = "TEXT")
    private String backdropUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "backdrop_media_id")
    private com.scriptles.cabinet.media.entity.Media backdropMedia;

    @Column(name = "backdrop_key", columnDefinition = "TEXT")
    private String backdropKey;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private ExternalSource originSource;

    @Column(length = 700)
    private String originKey;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "media_list_tags",
            joinColumns = @JoinColumn(name = "list_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"),
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_media_list_tag",
                    columnNames = {"list_id", "tag_id"})
    )
    @OrderBy("name asc")
    private Set<com.scriptles.cabinet.user.entity.UserTag> tags =
            new LinkedHashSet<>();

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
