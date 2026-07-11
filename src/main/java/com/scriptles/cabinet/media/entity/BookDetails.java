package com.scriptles.cabinet.media.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "book_details")
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class BookDetails {
    @Id
    @Column(name = "media_id")
    private UUID id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "media_id", nullable = false)
    private Media media;

    @Column(length = 10)
    private String isbn10;

    @Column(length = 13)
    private String isbn13;

    private Integer pageCount;

    private String publisher;

    private LocalDate publicationDate;

    @CreationTimestamp
    private Instant createdAt;
    @UpdateTimestamp
    private Instant updatedAt;
}
