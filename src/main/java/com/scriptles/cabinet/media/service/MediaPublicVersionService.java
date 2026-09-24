package com.scriptles.cabinet.media.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MediaPublicVersionService {
    private final NamedParameterJdbcTemplate jdbc;

    public Optional<String> currentVersion(UUID mediaId, String locale) {
        String sql = """
                select concat_ws(':',
                    media.version::text,
                    media.sync_version::text,
                    to_char(media.updated_at at time zone 'UTC', 'YYYYMMDDHH24MISSUS'),
                    coalesce((
                        select to_char(translation.updated_at at time zone 'UTC', 'YYYYMMDDHH24MISSUS')
                        from media_translations translation
                        where translation.media_id = media.id and translation.locale = :locale
                    ), 'no-translation'),
                    coalesce((
                        select event.id::text
                        from domain_outbox_events event
                        where event.aggregate_type = 'MEDIA'
                          and event.aggregate_id = media.id
                          and event.event_type in ('MEDIA_IMPORTED', 'MEDIA_METADATA_CHANGED')
                        order by event.created_at desc, event.id desc
                        limit 1
                    ), 'no-metadata-event'),
                    coalesce((
                        select to_char(max(version.last_synced_at) at time zone 'UTC', 'YYYYMMDDHH24MISSUS')
                        from album_release_versions version
                        where version.album_media_id = media.id
                    ), 'no-release-version-sync')
                )
                from media
                where media.id = :mediaId and media.catalog_status = 'READY'
                """;
        List<String> versions = jdbc.query(sql,
                new MapSqlParameterSource().addValue("mediaId", mediaId).addValue("locale", locale),
                (row, index) -> row.getString(1));
        return versions.stream().findFirst();
    }
}
