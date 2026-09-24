package com.scriptles.cabinet.media.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "genre")
@Getter
@Setter
public class Genre {
    @Id
    private UUID id;

    @Column(name = "canonical_key", nullable = false, length = 150)
    private String canonicalKey;

    @Column(nullable = false)
    private boolean provisional;
}
