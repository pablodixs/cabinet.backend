package com.scriptles.cabinet.media.repository;

import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.MediaCredit;
import com.scriptles.cabinet.media.enums.CreditRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MediaCreditRepository extends JpaRepository<MediaCredit, UUID> {
    void deleteAllByMediaId(UUID mediaId);

    @EntityGraph(attributePaths = "person")
    List<MediaCredit> findAllByMediaIdInOrderByPositionAsc(Collection<UUID> mediaIds);

    @Query("select credit.media.id as mediaId, person.id as personId, person.name as personName, "
            + "credit.role as role, credit.position as position from MediaCredit credit "
            + "join credit.person person where credit.media.id in :mediaIds "
            + "and person.id in :personIds and ("
            + "credit.role in (com.scriptles.cabinet.media.enums.CreditRole.AUTHOR, "
            + "com.scriptles.cabinet.media.enums.CreditRole.CREATOR, "
            + "com.scriptles.cabinet.media.enums.CreditRole.DIRECTOR, "
            + "com.scriptles.cabinet.media.enums.CreditRole.ARTIST, "
            + "com.scriptles.cabinet.media.enums.CreditRole.FEATURED_ARTIST, "
            + "com.scriptles.cabinet.media.enums.CreditRole.COMPOSER) or "
            + "(credit.role in (com.scriptles.cabinet.media.enums.CreditRole.SCREENWRITER, "
            + "com.scriptles.cabinet.media.enums.CreditRole.PRODUCER) and credit.position <= 5) or "
            + "(credit.role = com.scriptles.cabinet.media.enums.CreditRole.ACTOR and credit.position <= 20)) "
            + "order by credit.position asc nulls last")
    List<CreditScoringProjection> findScoringCredits(
            @Param("mediaIds") Collection<UUID> mediaIds,
            @Param("personIds") Collection<UUID> personIds
    );

    @Query("select credit.media.id as mediaId, person.id as personId, person.name as personName, "
            + "credit.role as role, credit.position as position from MediaCredit credit "
            + "join credit.person person where credit.media.id in :mediaIds and ("
            + "credit.role in (com.scriptles.cabinet.media.enums.CreditRole.AUTHOR, "
            + "com.scriptles.cabinet.media.enums.CreditRole.CREATOR, "
            + "com.scriptles.cabinet.media.enums.CreditRole.DIRECTOR, "
            + "com.scriptles.cabinet.media.enums.CreditRole.ARTIST, "
            + "com.scriptles.cabinet.media.enums.CreditRole.FEATURED_ARTIST, "
            + "com.scriptles.cabinet.media.enums.CreditRole.COMPOSER) or "
            + "(credit.role in (com.scriptles.cabinet.media.enums.CreditRole.SCREENWRITER, "
            + "com.scriptles.cabinet.media.enums.CreditRole.PRODUCER) and credit.position <= 5) or "
            + "(credit.role = com.scriptles.cabinet.media.enums.CreditRole.ACTOR and credit.position <= 20)) "
            + "order by credit.position asc nulls last")
    List<CreditScoringProjection> findPrincipalScoringCredits(@Param("mediaIds") Collection<UUID> mediaIds);

    @Query("select distinct credit.media.id as mediaId, person.name as personName, credit.role as role, "
            + "credit.position as position "
            + "from MediaCredit credit join credit.person person "
            + "where credit.media.id in :mediaIds and credit.role in :roles "
            + "order by credit.media.id, credit.position asc nulls last")
    List<CreditHeadlineProjection> findHeadlines(
            @Param("mediaIds") Collection<UUID> mediaIds,
            @Param("roles") Collection<CreditRole> roles
    );

    @Query(value = "with input_events as ("
            + "select event_id, media_id, source_media_id, event_date "
            + "from jsonb_to_recordset(cast(:eventsJson as jsonb)) "
            + "as input_event(event_id bigint, media_id uuid, source_media_id uuid, event_date date)"
            + "), resolved_events as ("
            + "select source.event_id, source.event_date, "
            + "case when source.source_media_id is not null and exists ("
            + "select 1 from media_credits source_credit "
            + "where source_credit.media_id = source.source_media_id and source_credit.role = :role"
            + ") then source.source_media_id else source.media_id end as credit_media_id "
            + "from input_events source"
            + "), event_credits as ("
            + "select distinct resolved.event_id, resolved.event_date, person.id as person_id, "
            + "person.name as person_name, person.image_url as person_image_url "
            + "from resolved_events resolved "
            + "join media_credits credit on credit.media_id = resolved.credit_media_id and credit.role = :role "
            + "join people person on person.id = credit.person_id"
            + "), ranking as ("
            + "select person_id, person_name, person_image_url, count(*) as event_count, "
            + "max(event_date) as last_date from event_credits "
            + "group by person_id, person_name, person_image_url"
            + "), limited as ("
            + "select ranking.*, row_number() over (order by event_count desc, last_date desc, "
            + "person_id::text asc) as rank_position from ranking"
            + "), totals as ("
            + "select (select count(*) from input_events) as eligible_event_count, "
            + "(select count(distinct event_id) from event_credits) as attributed_event_count"
            + ") select limited.person_id as \"personId\", limited.person_name as \"personName\", "
            + "limited.person_image_url as \"personImageUrl\", limited.event_count as \"eventCount\", "
            + "totals.eligible_event_count as \"eligibleEventCount\", "
            + "totals.attributed_event_count as \"attributedEventCount\" "
            + "from totals left join limited on limited.rank_position <= :limit "
            + "order by limited.rank_position nulls last", nativeQuery = true)
    List<ReportRankingProjection> rankReportPeople(
            @Param("eventsJson") String eventsJson,
            @Param("role") String role,
            @Param("limit") int limit
    );

    @Query(value = "select person.id as personId, person.name as name, credit.role as role, "
            + "credit.characterName as characterName, credit.position as position, person.imageUrl as imageUrl, "
            + "coalesce(credit.source, person.externalSource) as source, "
            + "coalesce(credit.externalId, person.externalId) as externalId "
            + "from MediaCredit credit join credit.person person "
            + "where credit.media.id = :mediaId and credit.role = :role "
            + "order by credit.position asc nulls last, credit.id asc",
            countQuery = "select count(credit.id) from MediaCredit credit "
                    + "where credit.media.id = :mediaId and credit.role = :role")
    Page<CreditDetailsProjection> findCreditDetailsByRole(
            @Param("mediaId") UUID mediaId,
            @Param("role") CreditRole role,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "media")
    List<MediaCredit> findAllByPersonId(UUID personId);

    @Query(
            value = "select distinct credit.media from MediaCredit credit "
                    + "where credit.person.id = :personId "
                    + "order by credit.media.releaseDate desc nulls last, credit.media.title asc",
            countQuery = "select count(distinct credit.media.id) from MediaCredit credit "
                    + "where credit.person.id = :personId"
    )
    Page<Media> findMediaByPersonId(@Param("personId") UUID personId, Pageable pageable);

    @EntityGraph(attributePaths = "media")
    List<MediaCredit> findAllByPersonIdAndMediaIdInOrderByPositionAsc(
            UUID personId,
            Collection<UUID> mediaIds
    );

    @Query("select count(distinct credit.media.id) from MediaCredit credit "
            + "where credit.person.id = :personId")
    long countDistinctMediaByPersonId(@Param("personId") UUID personId);

    @Query("select distinct credit.media.id from MediaCredit credit where credit.person.id = :personId")
    List<UUID> findDistinctMediaIdsByPersonId(@Param("personId") UUID personId);

    @Query("select distinct credit.role from MediaCredit credit where credit.person.id = :personId")
    List<CreditRole> findDistinctRolesByPersonId(@Param("personId") UUID personId);

    @EntityGraph(attributePaths = "person")
    @Query("select credit from MediaCredit credit "
            + "where credit.media.id = :mediaId and credit.role = :role "
            + "order by credit.position asc nulls last, credit.id asc")
    List<MediaCredit> findPrincipalByMediaIdAndRole(
            @Param("mediaId") UUID mediaId,
            @Param("role") CreditRole role,
            Pageable pageable
    );

    @Query("select media from Media media "
            + "where media.typeValue = :#{#mediaType.name()} and media.id <> :excludedMediaId "
            + "and exists (select credit.id from MediaCredit credit "
            + "where credit.media = media and credit.person.id = :personId and credit.role = :role) "
            + "order by media.releaseDate desc nulls last, media.title asc, media.id asc")
    List<Media> findLocalWorks(
            @Param("personId") UUID personId,
            @Param("role") CreditRole role,
            @Param("mediaType") com.scriptles.cabinet.media.enums.MediaType mediaType,
            @Param("excludedMediaId") UUID excludedMediaId,
            Pageable pageable
    );
}
