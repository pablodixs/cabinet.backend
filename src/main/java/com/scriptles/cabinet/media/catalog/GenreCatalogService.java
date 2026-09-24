package com.scriptles.cabinet.media.catalog;

import com.scriptles.cabinet.media.external.ExternalMedia;
import com.scriptles.cabinet.media.enums.ExternalSource;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GenreCatalogService {
    private final JdbcTemplate jdbc;
    public record GenreValue(UUID id, String name) {}

    public String normalize(String value) {
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    @Transactional
    public UUID resolve(String name, String locale, ExternalSource source, String externalId) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Genre name is required");
        String normalized = normalize(name);
        String language = "en-US".equals(locale) ? "en-US" : "pt-BR";
        String key = "legacy:" + language + ":" + normalized;
        UUID id = null;
        if (source != null && source != ExternalSource.MANUAL && externalId != null && !externalId.isBlank()) {
            List<UUID> matches = jdbc.query("select genre_id from genre_external_ref where source = ? and external_id = ?",
                    (rs, row) -> rs.getObject(1, UUID.class), source.name(), externalId);
            if (!matches.isEmpty()) id = matches.getFirst();
        }
        if (id == null) {
            List<UUID> aliases = jdbc.query(
                    "select genre_id from genre_alias where locale = ? and normalized_name = ?",
                    (rs, row) -> rs.getObject(1, UUID.class), language, normalized);
            if (!aliases.isEmpty()) id = aliases.getFirst();
        }
        if (id == null) {
            id = UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
            jdbc.update("insert into genre(id, canonical_key, provisional) values (?, ?, ?) on conflict (canonical_key) do nothing",
                    id, key, true);
            id = jdbc.queryForObject("select id from genre where canonical_key = ?", UUID.class, key);
        }
        if (source != null && source != ExternalSource.MANUAL && externalId != null && !externalId.isBlank()) {
            jdbc.update("insert into genre_external_ref(genre_id, source, external_id) values (?, ?, ?) on conflict (source, external_id) do nothing",
                    id, source.name(), externalId);
        }
        jdbc.update("insert into genre_translation(genre_id, locale, name, normalized_name) values (?, ?, ?, ?) " +
                        "on conflict (genre_id, locale) do nothing", id, language, name.trim(), normalized);
        return id;
    }

    @Transactional
    public void replace(UUID mediaId, Collection<ExternalMedia.ExternalGenre> genres, String locale) {
        Map<UUID, Boolean> ids = new LinkedHashMap<>();
        if (genres != null) for (ExternalMedia.ExternalGenre genre : genres) {
            if (genre.name() != null && !genre.name().isBlank())
                ids.put(resolve(genre.name(), locale, genre.source(), genre.id()), true);
        }
        jdbc.update("delete from media_genre where media_id = ?", mediaId);
        ids.keySet().forEach(id -> jdbc.update(
                "insert into media_genre(media_id, genre_id) values (?, ?) on conflict do nothing", mediaId, id));
    }

    @Transactional
    public void replaceNames(UUID mediaId, Collection<String> names, String locale) {
        replace(mediaId, names == null ? List.of() : names.stream()
                .map(name -> new ExternalMedia.ExternalGenre(null, name, ExternalSource.MANUAL)).toList(), locale);
    }

    @Transactional
    public void add(UUID mediaId, Collection<ExternalMedia.ExternalGenre> genres, String locale) {
        if (genres == null) return;
        for (ExternalMedia.ExternalGenre genre : genres) {
            if (genre.name() == null || genre.name().isBlank()) continue;
            UUID id = resolve(genre.name(), locale, genre.source(), genre.id());
            jdbc.update("insert into media_genre(media_id, genre_id) values (?, ?) on conflict do nothing", mediaId, id);
        }
    }

    public List<GenreValue> forMedia(UUID mediaId, String locale) {
        return jdbc.query("""
                select g.id, coalesce(requested.name, fallback.name) name
                from media_genre mg join genre g on g.id = mg.genre_id
                left join genre_translation requested on requested.genre_id = g.id and requested.locale = ?
                left join lateral (select name from genre_translation where genre_id = g.id
                    order by case when locale = 'pt-BR' then 0 else 1 end limit 1) fallback on true
                where mg.media_id = ? order by name
                """, (rs, row) -> new GenreValue(rs.getObject(1, UUID.class), rs.getString(2)), locale, mediaId);
    }

    public Map<UUID, List<GenreValue>> forMediaIds(Collection<UUID> mediaIds, String locale) {
        if (mediaIds.isEmpty()) return Map.of();
        if (mediaIds.size() > 500) {
            Map<UUID, List<GenreValue>> combined = new LinkedHashMap<>();
            List<UUID> ids = List.copyOf(mediaIds);
            for (int start = 0; start < ids.size(); start += 500) {
                combined.putAll(forMediaIds(ids.subList(start, Math.min(start + 500, ids.size())), locale));
            }
            return combined;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(mediaIds.size(), "?"));
        Object[] args = new Object[mediaIds.size() + 1];
        args[0] = locale;
        int index = 1;
        for (UUID id : mediaIds) args[index++] = id;
        Map<UUID, List<GenreValue>> result = new LinkedHashMap<>();
        jdbc.query("select mg.media_id, g.id, coalesce(requested.name, fallback.name) name " +
                "from media_genre mg join genre g on g.id = mg.genre_id " +
                "left join genre_translation requested on requested.genre_id = g.id and requested.locale = ? " +
                "left join lateral (select name from genre_translation where genre_id = g.id " +
                "order by case when locale = 'pt-BR' then 0 else 1 end limit 1) fallback on true " +
                "where mg.media_id in (" + placeholders + ")", rs -> {
                    UUID mediaId = rs.getObject(1, UUID.class);
                    result.computeIfAbsent(mediaId, ignored -> new java.util.ArrayList<>())
                            .add(new GenreValue(rs.getObject(2, UUID.class), rs.getString(3)));
                }, args);
        return result;
    }

    public List<GenreValue> options(String query, String locale, String type, int limit) {
        return jdbc.query("""
                select distinct g.id, coalesce(requested.name, fallback.name) name
                from genre g
                left join genre_translation requested on requested.genre_id = g.id and requested.locale = ?
                left join lateral (select name from genre_translation where genre_id = g.id
                    order by case when locale = 'pt-BR' then 0 else 1 end limit 1) fallback on true
                where exists (select 1 from media_genre mg join media m on m.id = mg.media_id
                    where mg.genre_id = g.id and (? is null or m.type = ?))
                  and exists (select 1 from genre_translation search where search.genre_id = g.id
                    and search.normalized_name like '%' || lower(?) || '%')
                order by name limit ?
                """, (rs, row) -> new GenreValue(rs.getObject(1, UUID.class), rs.getString(2)),
                locale, type, type, query, limit);
    }

    public List<GenreValue> libraryOptions(UUID userId, boolean includePrivate, String locale) {
        return jdbc.query("""
                select distinct g.id, coalesce(requested.name, fallback.name) name
                from user_media um join media_genre mg on mg.media_id = um.media_id
                join genre g on g.id = mg.genre_id
                left join genre_translation requested on requested.genre_id = g.id and requested.locale = ?
                left join lateral (select name from genre_translation where genre_id = g.id
                    order by case when locale = 'pt-BR' then 0 else 1 end limit 1) fallback on true
                where um.user_id = ? and (? or um.private_entry = false)
                order by name
                """, (rs, row) -> new GenreValue(rs.getObject(1, UUID.class), rs.getString(2)),
                locale, userId, includePrivate);
    }

    public String label(UUID id, String locale) {
        List<String> labels = jdbc.query("select name from genre_translation where genre_id = ? " +
                        "order by case when locale = ? then 0 when locale = 'pt-BR' then 1 else 2 end limit 1",
                (rs, row) -> rs.getString(1), id, locale);
        return labels.isEmpty() ? null : labels.getFirst();
    }

    public UUID uniqueLegacyId(String value) {
        try {
            UUID id = UUID.fromString(value);
            Integer count = jdbc.queryForObject("select count(*) from genre where id = ?", Integer.class, id);
            return count != null && count == 1 ? id : null;
        } catch (IllegalArgumentException ignored) {
            List<UUID> ids = jdbc.query("select distinct genre_id from genre_translation where normalized_name = ?",
                    (rs, row) -> rs.getObject(1, UUID.class), normalize(value));
            return ids.size() == 1 ? ids.getFirst() : null;
        }
    }
}
