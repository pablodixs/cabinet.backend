package com.scriptles.cabinet.media.entity;

import com.scriptles.cabinet.media.enums.AwardDatePrecision;
import com.scriptles.cabinet.media.enums.AwardOrigin;
import com.scriptles.cabinet.media.enums.AwardResult;
import com.scriptles.cabinet.user.entity.User;
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
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Check(name = "ck_award_entry_subject", constraints = """
        (media_id is not null and person_id is null)
        or (media_id is null and person_id is not null)
        """)
@Table(name = "award_entries", indexes = {
        @Index(name = "idx_award_entries_media_page",
                columnList = "media_id, hidden, event_year, event_date, category_name, id"),
        @Index(name = "idx_award_entries_person_page",
                columnList = "person_id, hidden, event_year, event_date, category_name, id"),
        @Index(name = "idx_award_entries_media_result", columnList = "media_id, hidden, result"),
        @Index(name = "idx_award_entries_person_result", columnList = "person_id, hidden, result"),
        @Index(name = "idx_award_entries_source_statement", columnList = "source_statement_id")
})
@Getter
@Setter
@NoArgsConstructor
public class AwardEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_id")
    private Media media;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id")
    private Person person;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AwardResult result;

    @Column(length = 30)
    private String programQid;

    @Column(length = 300)
    private String programName;

    @Column(length = 30)
    private String categoryQid;

    @Column(nullable = false, length = 300)
    private String categoryName;

    @Column(length = 30)
    private String ceremonyQid;

    @Column(length = 300)
    private String ceremonyName;

    private LocalDate eventDate;

    private Integer eventYear;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private AwardDatePrecision datePrecision;

    @Column(length = 30)
    private String workQid;

    @Column(length = 300)
    private String workName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_media_id")
    private Media workMedia;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AwardOrigin origin;

    @Column(name = "source_statement_id", length = 500, unique = true)
    private String sourceStatementId;

    @Column(columnDefinition = "TEXT")
    private String sourceUrl;

    @Column(nullable = false)
    private boolean curated;

    @Column(nullable = false)
    private boolean hidden;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "curated_by_user_id")
    private User curatedBy;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    @Version
    @Column(nullable = false, columnDefinition = "bigint default 0")
    private long version;
}
