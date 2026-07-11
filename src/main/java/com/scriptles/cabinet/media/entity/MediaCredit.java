package com.scriptles.cabinet.media.entity;

import com.scriptles.cabinet.media.enums.CreditRole;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "media_credits")
@Getter
@Setter
public class MediaCredit {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "media_id",
        nullable = false
    )
    private Media media;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "person_id",
        nullable = false
    )
    private Person person;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CreditRole role;

    @Column(length = 200)
    private String characterName;

    private Integer position;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
