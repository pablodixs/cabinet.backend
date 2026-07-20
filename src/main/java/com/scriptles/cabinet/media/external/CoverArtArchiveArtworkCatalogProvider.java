package com.scriptles.cabinet.media.external;

import com.scriptles.cabinet.media.config.ExternalApiProperties;
import com.scriptles.cabinet.media.enums.ArtworkProvider;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Component
@RequiredArgsConstructor
public class CoverArtArchiveArtworkCatalogProvider implements MediaArtworkCatalogProvider {
    private static final String BASE_URL = "https://coverartarchive.org";
    private static final int MAX_CACHE_ENTRIES = 2_000;
    private static final Duration HIT_TTL = Duration.ofHours(12);
    private static final Duration MISS_TTL = Duration.ofMinutes(15);

    private final RestClient.Builder restClientBuilder;
    private final ExternalApiProperties properties;
    private final Map<String, CacheEntry> cache = Collections.synchronizedMap(
            new LinkedHashMap<>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                    return size() > MAX_CACHE_ENTRIES;
                }
            }
    );

    @Override
    public boolean supports(ExternalSource source, MediaType type) {
        return source == ExternalSource.MUSICBRAINZ && type == MediaType.ALBUM;
    }

    @Override
    public ArtworkCatalog find(MediaType type, String externalId, String language) {
        Instant now = Instant.now();
        CacheEntry cached = cache.get(externalId);
        if (cached != null && cached.expiresAt().isAfter(now)) return cached.catalog();

        ArtworkCatalog catalog;
        Duration ttl;
        try {
            JsonNode body = restClientBuilder.clone().baseUrl(BASE_URL).build().get()
                    .uri("/release-group/{id}", externalId)
                    .header("User-Agent", properties.musicbrainz().userAgent())
                    .retrieve()
                    .body(JsonNode.class);
            Map<String, ArtworkAsset> covers = new LinkedHashMap<>();
            for (JsonNode image : body.path("images")) {
                if (!image.path("front").asBoolean(false)) continue;
                String url = text(image, "image");
                if (url == null) continue;
                String key = text(image, "id");
                if (key == null) key = url;
                String preview = text(image.path("thumbnails"), "500");
                if (preview == null) preview = text(image.path("thumbnails"), "large");
                covers.putIfAbsent(key, new ArtworkAsset(key, url, preview == null ? url : preview, null, null, null));
            }
            catalog = new ArtworkCatalog(ArtworkProvider.COVER_ART_ARCHIVE, List.copyOf(covers.values()), List.of());
            ttl = covers.isEmpty() ? MISS_TTL : HIT_TTL;
        } catch (RestClientException exception) {
            catalog = new ArtworkCatalog(ArtworkProvider.COVER_ART_ARCHIVE, List.of(), List.of());
            ttl = MISS_TTL;
        }
        cache.put(externalId, new CacheEntry(catalog, now.plus(ttl)));
        return catalog;
    }

    private String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private record CacheEntry(ArtworkCatalog catalog, Instant expiresAt) {}
}
