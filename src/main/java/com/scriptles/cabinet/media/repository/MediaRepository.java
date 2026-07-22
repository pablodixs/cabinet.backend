package com.scriptles.cabinet.media.repository;

import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface MediaRepository extends JpaRepository<Media, UUID> {
    Page<Media> findByTitleContainingIgnoreCase(
            String title,
            Pageable pageable
    );

    Page<Media> findByTitleContainingIgnoreCaseOrOriginalTitleContainingIgnoreCase(
            String title,
            String originalTitle,
            Pageable pageable
    );

    Page<Media> findByTypeValue(
            String type,
            Pageable pageable
    );

    Optional<Media> findFirstByWikidataId(String wikidataId);

    @Query("select b.media from BookDetails b where b.canonicalWorkWikidataId = :wikidataId")
    Optional<Media> findFirstByCanonicalBookWorkWikidataId(@Param("wikidataId") String wikidataId);

    @EntityGraph(attributePaths = "genres")
    @Query("select distinct media from Media media where media.id in :mediaIds")
    List<Media> findAllWithGenresByIdIn(@Param("mediaIds") Collection<UUID> mediaIds);

    @EntityGraph(attributePaths = "genres")
    @Query("""
            select distinct media from Media media
            where media.typeValue in :types
              and media.id not in :excludedIds
              and (
                exists (select genreMedia.id from Media genreMedia join genreMedia.genres genre
                        where genreMedia = media and lower(trim(genre)) in :genreKeys)
                or exists (select credit.id from MediaCredit credit
                           where credit.media = media and credit.person.id in :personIds)
              )
            order by lower(media.title), media.id
            """)
    List<Media> findInterestCandidates(
            @Param("types") Set<String> types,
            @Param("excludedIds") Collection<UUID> excludedIds,
            @Param("genreKeys") Collection<String> genreKeys,
            @Param("personIds") Collection<UUID> personIds,
            Pageable pageable
    );

    @Query("""
            select distinct genre from Media media join media.genres genre
            where (:type is null or media.typeValue = :type)
              and lower(genre) like lower(concat('%', :query, '%'))
            order by genre
            """)
    List<String> findGenreOptions(
            @Param("type") String type,
            @Param("query") String query,
            Pageable pageable
    );

    @Query("""
            select distinct genre from Media media join media.genres genre
            where lower(trim(genre)) = :genreKey
            order by genre
            """)
    List<String> findGenreLabels(@Param("genreKey") String genreKey, Pageable pageable);

    @EntityGraph(attributePaths = "genres")
    @Query("""
            select media from Media media
            where media.typeValue in :types
              and (lower(media.title) like lower(concat('%', :query, '%'))
                   or lower(coalesce(media.originalTitle, '')) like lower(concat('%', :query, '%')))
            order by lower(media.title), media.id
            """)
    List<Media> findInterestMediaOptions(
            @Param("types") Set<String> types,
            @Param("query") String query,
            Pageable pageable
    );
}
