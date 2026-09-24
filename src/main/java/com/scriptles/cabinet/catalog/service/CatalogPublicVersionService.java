package com.scriptles.cabinet.catalog.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CatalogPublicVersionService {
    private final NamedParameterJdbcTemplate jdbc;

    public Optional<String> collectionVersion(UUID collectionId, String locale) {
        String sql = """
                select concat_ws(':',
                    coalesce(to_char(collection.updated_at at time zone 'UTC', 'YYYYMMDDHH24MISSUS'), 'no-update'),
                    coalesce(to_char(collection.last_synced_at at time zone 'UTC', 'YYYYMMDDHH24MISSUS'), 'no-sync'),
                    coalesce((
                        select to_char(translation.updated_at at time zone 'UTC', 'YYYYMMDDHH24MISSUS')
                        from collection_translations translation
                        where translation.collection_id = collection.id and translation.locale = :locale
                    ), 'no-translation'),
                    (select count(*) from collection_items item where item.collection_id = collection.id)::text,
                    coalesce((select to_char(max(item.updated_at) at time zone 'UTC', 'YYYYMMDDHH24MISSUS')
                              from collection_items item where item.collection_id = collection.id), 'no-item-update'),
                    coalesce((select md5(string_agg(concat_ws(':', item.id, item.media_id, item.position,
                                                              item.section_id, item.relation_type), ',' order by item.id))
                              from collection_items item where item.collection_id = collection.id), 'no-items'),
                    coalesce((select to_char(max(media.updated_at) at time zone 'UTC', 'YYYYMMDDHH24MISSUS')
                              from collection_items item join media on media.id = item.media_id
                              where item.collection_id = collection.id), 'no-media-update'),
                    coalesce((select to_char(max(translation.updated_at) at time zone 'UTC', 'YYYYMMDDHH24MISSUS')
                              from collection_items item
                              join media_translations translation on translation.media_id = item.media_id
                              where item.collection_id = collection.id and translation.locale = :locale), 'no-media-translation'),
                    (select count(*) from collection_sections section where section.collection_id = collection.id)::text,
                    coalesce((select to_char(max(section.updated_at) at time zone 'UTC', 'YYYYMMDDHH24MISSUS')
                              from collection_sections section where section.collection_id = collection.id), 'no-section-update'),
                    (select count(*) from collection_source_items source where source.collection_id = collection.id)::text,
                    coalesce((select to_char(max(source.updated_at) at time zone 'UTC', 'YYYYMMDDHH24MISSUS')
                              from collection_source_items source where source.collection_id = collection.id), 'no-source-update'),
                    coalesce((select md5(string_agg(concat_ws(':', source.id, source.external_id,
                                                              source.resolved_media_id, source.title,
                                                              source.poster_url, source.release_date,
                                                              source.position, source.resolution_status), ',' order by source.id))
                              from collection_source_items source
                              where source.collection_id = collection.id and source.source_present), 'no-source-items'),
                    (select count(*) from franchise_collections link where link.collection_id = collection.id)::text,
                    coalesce((select to_char(max(franchise.updated_at) at time zone 'UTC', 'YYYYMMDDHH24MISSUS')
                              from franchise_collections link join franchises franchise on franchise.id = link.franchise_id
                              where link.collection_id = collection.id), 'no-franchise-update')
                )
                from collections collection
                where collection.id = :id
                """;
        return queryVersion(sql, new MapSqlParameterSource().addValue("id", collectionId).addValue("locale", locale));
    }

    public Optional<String> franchiseVersion(UUID franchiseId) {
        String sql = """
                select concat_ws(':',
                    coalesce(to_char(franchise.updated_at at time zone 'UTC', 'YYYYMMDDHH24MISSUS'), 'no-update'),
                    coalesce((select to_char(parent.updated_at at time zone 'UTC', 'YYYYMMDDHH24MISSUS')
                              from franchises parent where parent.id = franchise.parent_id), 'no-parent'),
                    (select count(*) from franchises child where child.parent_id = franchise.id)::text,
                    coalesce((select to_char(max(child.updated_at) at time zone 'UTC', 'YYYYMMDDHH24MISSUS')
                              from franchises child where child.parent_id = franchise.id), 'no-child-update'),
                    (select count(*) from franchise_media link where link.franchise_id = franchise.id)::text,
                    coalesce((select to_char(max(media.updated_at) at time zone 'UTC', 'YYYYMMDDHH24MISSUS')
                              from franchise_media link join media on media.id = link.media_id
                              where link.franchise_id = franchise.id), 'no-media-update'),
                    coalesce((select md5(string_agg(concat_ws(':', link.media_id, link.relation_type, link.is_primary,
                                                              media.title, media.type), ',' order by link.media_id))
                              from franchise_media link join media on media.id = link.media_id
                              where link.franchise_id = franchise.id), 'no-media-links'),
                    (select count(*) from franchise_collections link where link.franchise_id = franchise.id)::text,
                    coalesce((select to_char(max(collection.updated_at) at time zone 'UTC', 'YYYYMMDDHH24MISSUS')
                              from franchise_collections link join collections collection on collection.id = link.collection_id
                              where link.franchise_id = franchise.id), 'no-collection-update'),
                    coalesce((select to_char(max(item.updated_at) at time zone 'UTC', 'YYYYMMDDHH24MISSUS')
                              from franchise_collections link join collection_items item on item.collection_id = link.collection_id
                              where link.franchise_id = franchise.id), 'no-collection-item-update'),
                    coalesce((select md5(string_agg(concat_ws(':', link.collection_id, link.relation_type,
                                                              link.position, collection.title,
                                                              (select count(*) from collection_items item
                                                               where item.collection_id = collection.id)), ',' order by link.position, link.collection_id))
                              from franchise_collections link
                              join collections collection on collection.id = link.collection_id
                              where link.franchise_id = franchise.id), 'no-collection-links')
                )
                from franchises franchise
                where franchise.id = :id
                """;
        return queryVersion(sql, new MapSqlParameterSource("id", franchiseId));
    }

    private Optional<String> queryVersion(String sql, MapSqlParameterSource parameters) {
        List<String> versions = jdbc.query(sql, parameters, (row, index) -> row.getString(1));
        return versions.stream().findFirst();
    }
}
