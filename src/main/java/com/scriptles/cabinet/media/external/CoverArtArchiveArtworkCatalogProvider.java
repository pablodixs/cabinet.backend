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
    private static final int MAX_RELEASES = 8;

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

        Map<String, ArtworkAsset> covers = new LinkedHashMap<>();
        try {
            JsonNode releaseGroup = restClientBuilder.clone().baseUrl(properties.musicbrainz().baseUrl()).build().get()
                    .uri(uriBuilder -> uriBuilder.path("/release-group/{id}")
                            .queryParam("inc", "releases")
                            .queryParam("fmt", "json")
                            .build(externalId))
                    .header("User-Agent", properties.musicbrainz().userAgent())
                    .retrieve()
                    .body(JsonNode.class);
            List<JsonNode> releases = new ArrayList<>();
            releaseGroup.path("releases").forEach(releases::add);
            releases.sort(Comparator
                    .comparing((JsonNode release) -> text(release, "date"), Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(release -> Objects.toString(text(release, "country"), ""))
                    .thenComparing(release -> Objects.toString(text(release, "id"), "")));

            int fetched = 0;
            for (JsonNode release : releases) {
                if (fetched >= MAX_RELEASES) break;
                String releaseId = text(release, "id");
                if (releaseId == null) continue;
                fetched++;
                try {
                    JsonNode releaseArtwork = restClientBuilder.clone().baseUrl(BASE_URL).build().get()
                            .uri("/release/{id}", releaseId)
                            .header("User-Agent", properties.musicbrainz().userAgent())
                            .retrieve()
                            .body(JsonNode.class);
                    addImages(covers, releaseArtwork, "release:" + releaseId, releaseLabel(release));
                } catch (RestClientException ignored) {
                    // An edition without artwork is expected; keep results from other editions.
                }
            }
        } catch (RestClientException exception) {
            // Individual editions are optional; release-group artwork may still be available.
        }
        try {
            JsonNode groupArtwork = restClientBuilder.clone().baseUrl(BASE_URL).build().get()
                    .uri("/release-group/{id}", externalId)
                    .header("User-Agent", properties.musicbrainz().userAgent())
                    .retrieve()
                    .body(JsonNode.class);
            addImages(covers, groupArtwork, "release-group:" + externalId, "Grupo do álbum");
        } catch (RestClientException ignored) {
            // Release-group artwork is optional; keep results already fetched from editions.
        }
        ArtworkCatalog catalog = new ArtworkCatalog(ArtworkProvider.COVER_ART_ARCHIVE, List.copyOf(covers.values()), List.of());
        Duration ttl = catalog.covers().isEmpty() ? MISS_TTL : HIT_TTL;
        cache.put(externalId, new CacheEntry(catalog, now.plus(ttl)));
        return catalog;
    }

    private void addImages(Map<String, ArtworkAsset> covers, JsonNode body, String editionKey, String editionLabel) {
        for (JsonNode image : body.path("images")) {
            if (!image.path("front").asBoolean(false)) continue;
            String url = text(image, "image");
            if (url == null) continue;
            String imageId = text(image, "id");
            String key = editionKey.startsWith("release-group:")
                    ? (imageId == null ? url : imageId)
                    : editionKey + ":" + (imageId == null ? url : imageId);
            String preview = text(image.path("thumbnails"), "500");
            if (preview == null) preview = text(image.path("thumbnails"), "large");
            covers.putIfAbsent(url, new ArtworkAsset(
                    key, url, preview == null ? url : preview, null, null, null, editionLabel));
        }
    }

    private String releaseLabel(JsonNode release) {
        String title = text(release, "title");
        String date = text(release, "date");
        String country = text(release, "country");
        String year = date != null && date.length() >= 4 ? date.substring(0, 4) : null;
        List<String> parts = new ArrayList<>();
        if (title != null) parts.add(title);
        if (year != null) parts.add(year);
        if (country != null) parts.add(country);
        return parts.isEmpty() ? "Edição do álbum" : String.join(" · ", parts);
    }

    private String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private record CacheEntry(ArtworkCatalog catalog, Instant expiresAt) {}
}
