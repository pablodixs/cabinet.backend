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

    @EntityGraph(attributePaths = "person")
    Page<MediaCredit> findAllByMediaIdAndRoleOrderByPositionAscIdAsc(
            UUID mediaId,
            CreditRole role,
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
            + "where media.type = :mediaType and media.id <> :excludedMediaId "
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
