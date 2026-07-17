package com.scriptles.cabinet.media.external;

import com.scriptles.cabinet.media.config.ExternalApiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AlbumCoverService {
    private static final String COVER_ART_BASE_URL = "https://coverartarchive.org";
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

    public String findCoverUrl(String releaseGroupId) {
        if (releaseGroupId == null || releaseGroupId.isBlank()) {
            return null;
        }
        Instant now = Instant.now();
        CacheEntry cached = cache.get(releaseGroupId);
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.value().orElse(null);
        }

        Optional<String> resolved = resolveCoverUrl(releaseGroupId);
        Duration ttl = resolved.isPresent() ? HIT_TTL : MISS_TTL;
        cache.put(releaseGroupId, new CacheEntry(resolved, now.plus(ttl)));
        return resolved.orElse(null);
    }

    private Optional<String> resolveCoverUrl(String releaseGroupId) {
        return findCoverArtArchiveUrl(releaseGroupId)
                .or(() -> findTheAudioDbUrl(releaseGroupId));
    }

    private Optional<String> findCoverArtArchiveUrl(String releaseGroupId) {
        try {
            JsonNode body = restClientBuilder.clone().baseUrl(COVER_ART_BASE_URL).build().get()
                    .uri("/release-group/{id}", releaseGroupId)
                    .header("User-Agent", properties.musicbrainz().userAgent())
                    .retrieve()
                    .body(JsonNode.class);
            for (JsonNode image : body.path("images")) {
                if (image.path("front").asBoolean(false)) {
                    String thumbnail = text(image.path("thumbnails"), "500");
                    return Optional.ofNullable(thumbnail != null ? thumbnail : text(image, "image"));
                }
            }
        } catch (RestClientException exception) {
            // Missing cover art is expected and falls through to the next provider.
        }
        return Optional.empty();
    }

    private Optional<String> findTheAudioDbUrl(String releaseGroupId) {
        try {
            JsonNode body = restClientBuilder.clone().baseUrl(properties.theAudioDb().baseUrl()).build().get()
                    .uri("/{apiKey}/album-mb.php?i={id}", properties.theAudioDb().apiKey(), releaseGroupId)
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode album = body.path("album").path(0);
            String cover = text(album, "strAlbumThumbHQ");
            return Optional.ofNullable(cover != null ? cover : text(album, "strAlbumThumb"));
        } catch (RestClientException exception) {
            return Optional.empty();
        }
    }

    private String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private record CacheEntry(Optional<String> value, Instant expiresAt) {
    }
}
