package com.scriptles.cabinet.profile.entity;

import com.scriptles.cabinet.user.enums.Visibility;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity @Table(name = "hq_lists") @Getter @Setter @NoArgsConstructor
public class HQList {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "hq_profile_id", nullable = false) private HQProfile hqProfile;
    @Column(nullable = false, length = 120) private String name;
    @Column(columnDefinition = "TEXT") private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Visibility visibility = Visibility.PUBLIC;
    @Column(nullable = false) private boolean ordered = true;
    @Column(length = 500) private String coverUrl;
    @ManyToMany
    @JoinTable(name = "hq_list_editors", joinColumns = @JoinColumn(name = "list_id"), inverseJoinColumns = @JoinColumn(name = "operator_id"))
    private Set<HQOperator> editors = new HashSet<>();
    @CreationTimestamp private Instant createdAt;
    @UpdateTimestamp private Instant updatedAt;
}
