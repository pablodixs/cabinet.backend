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
    private final MusicBrainzClient musicBrainzClient;
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
            for (MusicBrainzClient.AlbumReleaseVersionSnapshot release
                    : musicBrainzClient.findAlbumReleaseVersions(externalId)) {
                if (release.coverUrl() == null) continue;
                String releaseId = release.musicBrainzReleaseId().toString();
                String url = BASE_URL + "/release/" + releaseId + "/front";
                covers.putIfAbsent(url, new ArtworkAsset(
                        "release:" + releaseId + ":front", url, release.coverUrl(),
                        null, null, null, releaseLabel(release)));
            }
        } catch (ExternalMediaException ignored) {
            // Keep release-group artwork available when MusicBrainz cannot return editions.
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

    private String releaseLabel(MusicBrainzClient.AlbumReleaseVersionSnapshot release) {
        List<String> parts = new ArrayList<>();
        if (release.title() != null) parts.add(release.title());
        if (release.releaseDate() != null) parts.add(Integer.toString(release.releaseDate().getYear()));
        if (release.countryCode() != null) parts.add(release.countryCode());
        return parts.isEmpty() ? "Edição do álbum" : String.join(" · ", parts);
    }

    private String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private record CacheEntry(ArtworkCatalog catalog, Instant expiresAt) {}
}
