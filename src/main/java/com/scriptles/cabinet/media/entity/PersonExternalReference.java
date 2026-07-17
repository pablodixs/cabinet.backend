package com.scriptles.cabinet.media.entity;

import com.scriptles.cabinet.media.enums.ExternalSource;
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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "person_external_references", uniqueConstraints = @UniqueConstraint(
        name = "uk_person_external_reference_source_id",
        columnNames = {"source", "external_id"}
), indexes = {
        @Index(name = "idx_person_external_references_person_id", columnList = "person_id"),
        @Index(name = "idx_person_external_references_source_id", columnList = "source, external_id")
})
@Getter
@Setter
@NoArgsConstructor
public class PersonExternalReference {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ExternalSource source;

    @Column(name = "external_id", nullable = false, length = 200)
    private String externalId;

    @Column(length = 500)
    private String externalUrl;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
