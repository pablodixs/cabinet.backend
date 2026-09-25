package com.scriptles.cabinet.media.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class MediaSearchDocumentRepository {
    private static final String[] SEARCHABLE_TYPES = {"MOVIE", "SERIES", "ALBUM", "BOOK"};

    private final NamedParameterJdbcTemplate jdbc;

    public List<UUID> search(String query, String mediaType, List<String> locales, int limit, int offset) {
        String sql = """
                WITH query_input AS (
                    SELECT unaccent('unaccent', lower(CAST(:query AS text))) AS normalized_query,
                           plainto_tsquery('simple', unaccent('unaccent', lower(CAST(:query AS text)))) AS terms
                ), candidates AS (
                    SELECT DISTINCT ON (document.media_id)
                           document.media_id,
                           (
                               CASE
                                   WHEN document.title_normalized = input.normalized_query THEN 100.0
                                   WHEN document.original_title_normalized = input.normalized_query THEN 95.0
                                   WHEN document.title_normalized LIKE unaccent('unaccent', lower(:prefixQuery)) || '%' ESCAPE E'\\\\' THEN 30.0
                                   WHEN document.original_title_normalized LIKE unaccent('unaccent', lower(:prefixQuery)) || '%' ESCAPE E'\\\\' THEN 27.0
                                   ELSE 0.0
                               END
                               + GREATEST(
                                   similarity(document.title_normalized, input.normalized_query),
                                   similarity(document.original_title_normalized, input.normalized_query)
                               ) * 20.0
                               + ts_rank_cd(document.search_vector, input.terms) * 10.0
                               + LEAST(1.0, LN(1.0 + GREATEST(0.0,
                                   COALESCE(stats.rating_count, 0)
                                   + COALESCE(stats.like_count, 0)
                                   + COALESCE(stats.list_count, 0)
                                   + COALESCE(stats.completed_count, 0)
                               ))) * 0.25
                               + CASE WHEN document.release_date IS NULL THEN 0.0 ELSE
                                   GREATEST(0.0, 1.0 - GREATEST(0, CURRENT_DATE - document.release_date) / 3650.0) * 0.5
                                 END
                           ) AS relevance
                    FROM media_search_documents document
                    CROSS JOIN query_input input
                    LEFT JOIN media_community_stats stats ON stats.media_id = document.media_id
                    WHERE document.locale IN (:locales)
                      AND (CAST(:mediaType AS text) IS NULL OR document.media_type = CAST(:mediaType AS text))
                      AND (
                          document.title_normalized LIKE unaccent('unaccent', lower(:prefixQuery)) || '%' ESCAPE E'\\\\'
                          OR document.original_title_normalized LIKE unaccent('unaccent', lower(:prefixQuery)) || '%' ESCAPE E'\\\\'
                          OR (length(input.normalized_query) >= 3 AND (
                              document.title_normalized % input.normalized_query
                              OR document.original_title_normalized % input.normalized_query
                          ))
                          OR document.search_vector @@ input.terms
                      )
                    ORDER BY document.media_id, relevance DESC, document.locale
                )
                SELECT media_id
                FROM candidates
                ORDER BY relevance DESC, media_id
                LIMIT :limit OFFSET :offset
                """;
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("query", query)
                .addValue("prefixQuery", escapeLikeQuery(query))
                .addValue("mediaType", mediaType)
                .addValue("locales", locales)
                .addValue("limit", limit)
                .addValue("offset", offset);
        return jdbc.query(sql, parameters, (row, index) -> row.getObject("media_id", UUID.class));
    }

    private String escapeLikeQuery(String query) {
        return query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    @Transactional
    public void rebuild(UUID mediaId) {
        jdbc.update("DELETE FROM media_search_documents WHERE media_id = :mediaId",
                new MapSqlParameterSource("mediaId", mediaId));
        jdbc.update("""
                WITH searchable_media AS (
                    SELECT media.*
                    FROM media media
                    WHERE media.id = :mediaId
                      AND media.type IN (:searchableTypes)
                ), locales AS (
                    SELECT media.id AS media_id, media.default_locale AS locale
                    FROM searchable_media media
                    UNION
                    SELECT translation.media_id, translation.locale
                    FROM media_translations translation
                    JOIN searchable_media media ON media.id = translation.media_id
                ), source_data AS (
                    SELECT media.id AS media_id,
                           locales.locale,
                           media.type AS media_type,
                           COALESCE(NULLIF(translation.title, ''), media.title, media.original_title) AS title,
                           media.original_title,
                           COALESCE(credits.names, '') AS creator_names,
                           COALESCE(alternatives.names, ARRAY[]::TEXT[]) AS alternative_titles,
                           COALESCE(reference.source, 'MANUAL') AS external_source,
                           COALESCE(reference.external_id, media.id::text) AS external_id,
                           COALESCE(translation.description, media.description) AS description,
                           media.release_date,
                           COALESCE(stats.rating_count, 0)
                               + COALESCE(stats.like_count, 0)
                               + COALESCE(stats.list_count, 0)
                               + COALESCE(stats.completed_count, 0) AS popularity_score
                    FROM searchable_media media
                    JOIN locales ON locales.media_id = media.id
                    LEFT JOIN media_translations translation
                           ON translation.media_id = media.id
                          AND translation.locale = locales.locale
                          AND translation.translation_status IN ('AVAILABLE', 'PARTIAL')
                    LEFT JOIN LATERAL (
                        SELECT string_agg(DISTINCT person.name, ' ' ORDER BY person.name) AS names
                        FROM media_credits credit
                        JOIN people person ON person.id = credit.person_id
                        WHERE credit.media_id = media.id
                    ) credits ON TRUE
                    LEFT JOIN LATERAL (
                        SELECT array_agg(DISTINCT title ORDER BY title) AS names
                        FROM (
                            SELECT media.original_title AS title
                            UNION ALL
                            SELECT other_translation.title
                            FROM media_translations other_translation
                            WHERE other_translation.media_id = media.id
                              AND other_translation.translation_status IN ('AVAILABLE', 'PARTIAL')
                        ) title_values
                        WHERE NULLIF(title, '') IS NOT NULL
                    ) alternatives ON TRUE
                    LEFT JOIN LATERAL (
                        SELECT external_reference.source, external_reference.external_id
                        FROM external_references external_reference
                        WHERE external_reference.media_id = media.id
                          AND external_reference.primary_reference = TRUE
                        ORDER BY external_reference.id
                        LIMIT 1
                    ) reference ON TRUE
                    LEFT JOIN media_community_stats stats ON stats.media_id = media.id
                ), normalized AS (
                    SELECT source_data.*,
                           unaccent('unaccent', lower(source_data.title)) AS title_normalized,
                           unaccent('unaccent', lower(COALESCE(source_data.original_title, ''))) AS original_title_normalized,
                           unaccent('unaccent', lower(concat_ws(' ',
                               source_data.title,
                               source_data.original_title,
                               source_data.creator_names,
                               array_to_string(source_data.alternative_titles, ' ')
                           ))) AS normalized_text
                    FROM source_data
                )
                INSERT INTO media_search_documents (
                    media_id, locale, media_type, title, title_normalized,
                    original_title, original_title_normalized, creator_names,
                    alternative_titles, normalized_text, search_vector,
                    external_source, external_id, description, release_date,
                    popularity_score, updated_at
                )
                SELECT media_id, locale, media_type, title, title_normalized,
                       original_title, original_title_normalized, creator_names,
                       alternative_titles, normalized_text,
                       to_tsvector('simple', normalized_text),
                       external_source, external_id, description, release_date,
                       popularity_score, now()
                FROM normalized
                ON CONFLICT (media_id, locale) DO UPDATE SET
                    media_type = EXCLUDED.media_type,
                    title = EXCLUDED.title,
                    title_normalized = EXCLUDED.title_normalized,
                    original_title = EXCLUDED.original_title,
                    original_title_normalized = EXCLUDED.original_title_normalized,
                    creator_names = EXCLUDED.creator_names,
                    alternative_titles = EXCLUDED.alternative_titles,
                    normalized_text = EXCLUDED.normalized_text,
                    search_vector = EXCLUDED.search_vector,
                    external_source = EXCLUDED.external_source,
                    external_id = EXCLUDED.external_id,
                    description = EXCLUDED.description,
                    release_date = EXCLUDED.release_date,
                    popularity_score = EXCLUDED.popularity_score,
                    updated_at = now()
                """, new MapSqlParameterSource()
                .addValue("mediaId", mediaId)
                .addValue("searchableTypes", List.of(SEARCHABLE_TYPES)));
    }

    public List<UUID> findMissingMediaIds(int limit) {
        return jdbc.query("""
                SELECT media.id
                FROM media
                WHERE media.type IN (:searchableTypes)
                  AND NOT EXISTS (
                      SELECT 1 FROM media_search_documents document WHERE document.media_id = media.id
                  )
                ORDER BY media.id
                LIMIT :limit
                """, new MapSqlParameterSource()
                .addValue("searchableTypes", List.of(SEARCHABLE_TYPES))
                .addValue("limit", limit), (row, index) -> row.getObject(1, UUID.class));
    }

    public List<UUID> findStaleMediaIds(Instant cutoff, int limit) {
        return jdbc.query("""
                SELECT document.media_id
                FROM media_search_documents document
                WHERE document.updated_at < :cutoff
                GROUP BY document.media_id
                ORDER BY MIN(document.updated_at), document.media_id
                LIMIT :limit
                """, new MapSqlParameterSource()
                .addValue("cutoff", cutoff)
                .addValue("limit", limit), (row, index) -> row.getObject(1, UUID.class));
    }
}
