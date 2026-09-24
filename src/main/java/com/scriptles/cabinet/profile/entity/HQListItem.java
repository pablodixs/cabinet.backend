package com.scriptles.cabinet.profile.entity;

import com.scriptles.cabinet.media.entity.Media;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.UUID;

@Entity @Table(name = "hq_list_items", uniqueConstraints = @UniqueConstraint(columnNames = {"list_id", "media_id"}))
@Getter @Setter @NoArgsConstructor
public class HQListItem {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "list_id", nullable = false) private HQList list;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "media_id", nullable = false) private Media media;
    @Column(nullable = false) private int position;
    @Column(columnDefinition = "TEXT") private String notes;
}
