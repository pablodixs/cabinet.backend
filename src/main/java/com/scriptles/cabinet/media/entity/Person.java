package com.scriptles.cabinet.media.entity;

import com.scriptles.cabinet.media.enums.ExternalSource;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "people", uniqueConstraints = @UniqueConstraint(
        name = "uk_people_external_reference",
        columnNames = {"external_source", "external_id"}
), indexes = {
        @Index(name = "idx_people_external_reference", columnList = "external_source, external_id"),
        @Index(name = "idx_people_name", columnList = "name")
})
@Getter
@Setter
@NoArgsConstructor
public class Person {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String biography;

    @Column(length = 500)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    private ExternalSource externalSource;

    @Column(length = 200)
    private String externalId;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
