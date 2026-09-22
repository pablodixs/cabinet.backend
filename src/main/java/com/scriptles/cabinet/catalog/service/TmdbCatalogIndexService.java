package com.scriptles.cabinet.catalog.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

@Service
@Slf4j
public class TmdbCatalogIndexService {
    private static final String UPSERT = """
            insert into external_catalog_entities
                (id, provider, entity_type, external_id, source_metadata, first_seen_at,
                 last_seen_at, last_index_run_id, state, removed_at, created_at, updated_at)
            values (gen_random_uuid(), 'TMDB', 'COLLECTION', ?, CAST(? AS jsonb), ?, ?, ?, 'ACTIVE', null, ?, ?)
            on conflict (provider, entity_type, external_id) do update set
                source_metadata = excluded.source_metadata,
                last_seen_at = excluded.last_seen_at,
                last_index_run_id = excluded.last_index_run_id,
                state = 'ACTIVE', removed_at = null, updated_at = excluded.updated_at
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final int batchSize;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;

    public TmdbCatalogIndexService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, MeterRegistry meterRegistry,
            @Value("${catalog.tmdb.index.batch-size:2000}") int batchSize,
            @Value("${catalog.tmdb.index.connect-timeout:30s}") java.time.Duration connectTimeout,
            @Value("${catalog.tmdb.index.read-timeout:5m}") java.time.Duration readTimeout) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.batchSize = Math.max(1, batchSize);
        this.connectTimeoutMillis = Math.toIntExact(connectTimeout.toMillis());
        this.readTimeoutMillis = Math.toIntExact(readTimeout.toMillis());
    }

    public IndexResult importCollections(String exportDate, UUID runId) {
        String url = "https://files.tmdb.org/p/exports/collection_ids_" + exportDate + ".json.gz";
        HttpURLConnection connection = null;
        long seen = 0;
        Instant now = Instant.now();
        List<Object[]> batch = new ArrayList<>(batchSize);
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(connectTimeoutMillis);
            connection.setReadTimeout(readTimeoutMillis);
            connection.setRequestProperty("Accept-Encoding", "gzip");
            connection.connect();
            try (GZIPInputStream gzip = new GZIPInputStream(connection.getInputStream());
                 BufferedReader reader = new BufferedReader(new InputStreamReader(gzip, StandardCharsets.UTF_8), 64 * 1024)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) continue;
                    JsonNode node = objectMapper.readTree(line);
                    String id = node.path("id").asText(null);
                    if (id == null || !id.matches("[1-9][0-9]*")) {
                        throw new IOException("TMDB export contained a row without a valid collection ID");
                    }
                    batch.add(new Object[]{id, metadataJson(node), now, now, runId, now, now});
                    seen++;
                    if (batch.size() >= batchSize) {
                        jdbcTemplate.batchUpdate(UPSERT, batch);
                        batch.clear();
                    }
                }
            }
            if (!batch.isEmpty()) jdbcTemplate.batchUpdate(UPSERT, batch);

            // This is intentionally the last write: a failed download or parse never marks unseen IDs removed.
            int removed = jdbcTemplate.update("""
                    update external_catalog_entities
                    set state = 'REMOVED', removed_at = ?, updated_at = ?
                    where provider = 'TMDB' and entity_type = 'COLLECTION'
                      and state = 'ACTIVE' and last_index_run_id is distinct from ?
                    """, now, now, runId);
            meterRegistry.counter("cabinet.catalog.external.entities.discovered", "provider", "TMDB",
                    "entity_type", "COLLECTION").increment(seen);
            return new IndexResult(seen, removed, exportDate);
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to complete TMDB collection ID export", failure);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private String metadataJson(JsonNode row) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (row.has("adult") && row.get("adult").isBoolean()) metadata.put("adult", row.get("adult").booleanValue());
        if (row.has("video") && row.get("video").isBoolean()) metadata.put("video", row.get("video").booleanValue());
        if (row.has("popularity") && row.get("popularity").isNumber()) {
            metadata.put("popularity", row.get("popularity").doubleValue());
        }
        return objectMapper.writeValueAsString(metadata);
    }

    public record IndexResult(long discovered, long removed, String exportDate) {
    }
}
