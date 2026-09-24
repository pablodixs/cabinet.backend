package com.scriptles.cabinet.user.entity;

import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.Person;
import com.scriptles.cabinet.user.enums.InterestPreference;
import com.scriptles.cabinet.user.enums.InterestTargetType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_interest_preferences", indexes = {
        @Index(name = "idx_user_interest_preferences_user", columnList = "user_id,target_type")
})
@Getter
@Setter
public class UserInterestPreference {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private InterestTargetType targetType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InterestPreference preference;

    @Column(name = "genre_key", length = 100)
    private String genreKey;

    @Column(name = "genre_label", length = 100)
    private String genreLabel;

    @Column(name = "genre_id")
    private UUID genreId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id")
    private Person person;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_id")
    private Media media;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
