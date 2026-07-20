package com.scriptles.cabinet.media.repository;

import com.scriptles.cabinet.media.entity.AwardEntry;
import com.scriptles.cabinet.media.enums.AwardOrigin;
import com.scriptles.cabinet.media.enums.AwardResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AwardEntryRepository extends JpaRepository<AwardEntry, UUID> {
    @Query("""
            select a from AwardEntry a
            where a.media.id = :subjectId and a.hidden = false
              and (:result is null or a.result = :result)
            order by case when a.eventYear is null then 1 else 0 end,
                     a.eventYear desc, a.eventDate desc, a.categoryName asc, a.id asc
            """)
    Page<AwardEntry> findVisibleByMediaId(
            @Param("subjectId") UUID subjectId,
            @Param("result") AwardResult result,
            Pageable pageable
    );

    @Query("""
            select a from AwardEntry a
            where a.person.id = :subjectId and a.hidden = false
              and (:result is null or a.result = :result)
            order by case when a.eventYear is null then 1 else 0 end,
                     a.eventYear desc, a.eventDate desc, a.categoryName asc, a.id asc
            """)
    Page<AwardEntry> findVisibleByPersonId(
            @Param("subjectId") UUID subjectId,
            @Param("result") AwardResult result,
            Pageable pageable
    );

    long countByMediaIdAndHiddenFalseAndResult(UUID mediaId, AwardResult result);

    long countByPersonIdAndHiddenFalseAndResult(UUID personId, AwardResult result);

    List<AwardEntry> findAllByMediaId(UUID mediaId);

    List<AwardEntry> findAllByPersonId(UUID personId);

    List<AwardEntry> findAllByMediaIdAndOriginAndCuratedFalse(UUID mediaId, AwardOrigin origin);

    List<AwardEntry> findAllByPersonIdAndOriginAndCuratedFalse(UUID personId, AwardOrigin origin);

    Optional<AwardEntry> findBySourceStatementId(String sourceStatementId);

    Optional<AwardEntry> findFirstByMediaIdAndCategoryQidAndCeremonyQidAndEventYearAndWorkQid(
            UUID mediaId, String categoryQid, String ceremonyQid, Integer eventYear, String workQid);

    Optional<AwardEntry> findFirstByPersonIdAndCategoryQidAndCeremonyQidAndEventYearAndWorkQid(
            UUID personId, String categoryQid, String ceremonyQid, Integer eventYear, String workQid);

    @Query("""
            select a from AwardEntry a where a.media.id = :subjectId
            order by a.hidden asc, case when a.eventYear is null then 1 else 0 end,
                     a.eventYear desc, a.categoryName asc, a.id asc
            """)
    Page<AwardEntry> findModerationByMediaId(@Param("subjectId") UUID subjectId, Pageable pageable);

    @Query("""
            select a from AwardEntry a where a.person.id = :subjectId
            order by a.hidden asc, case when a.eventYear is null then 1 else 0 end,
                     a.eventYear desc, a.categoryName asc, a.id asc
            """)
    Page<AwardEntry> findModerationByPersonId(@Param("subjectId") UUID subjectId, Pageable pageable);
}
