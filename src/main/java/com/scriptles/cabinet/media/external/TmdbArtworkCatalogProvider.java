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
public class TmdbArtworkCatalogProvider implements MediaArtworkCatalogProvider {
    private static final String IMAGE_BASE_URL = "https://image.tmdb.org/t/p";
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
        return source == ExternalSource.TMDB && (type == MediaType.MOVIE || type == MediaType.SERIES);
    }

    @Override
    public ArtworkCatalog find(MediaType mediaType, String externalId, String language) {
        String locale = language == null || language.isBlank() ? "pt" : language.substring(0, 2).toLowerCase(Locale.ROOT);
        String type = mediaType == MediaType.SERIES ? "tv" : "movie";
        String key = type + ':' + externalId + ':' + locale;
        Instant now = Instant.now();
        CacheEntry cached = cache.get(key);
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.catalog();
        }

        ArtworkCatalog catalog;
        Duration ttl;
        try {
            catalog = fetch(type, externalId, locale);
            ttl = catalog.covers().isEmpty() && catalog.backdrops().isEmpty() ? MISS_TTL : HIT_TTL;
        } catch (RestClientException exception) {
            catalog = new ArtworkCatalog(ArtworkProvider.TMDB, List.of(), List.of());
            ttl = MISS_TTL;
        }
        cache.put(key, new CacheEntry(catalog, now.plus(ttl)));
        return catalog;
    }

    private ArtworkCatalog fetch(String type, String externalId, String locale) {
        boolean bearer = hasText(properties.tmdb().accessToken());
        if (!bearer && !hasText(properties.tmdb().apiKey())) {
            return new ArtworkCatalog(ArtworkProvider.TMDB, List.of(), List.of());
        }

        RestClient.RequestHeadersSpec<?> request = restClientBuilder.clone()
                .baseUrl(properties.tmdb().baseUrl()).build().get()
                .uri(builder -> {
                    builder.path("/{type}/{id}/images")
                            .queryParam("include_image_language", locale + ",en,null");
                    if (!bearer) builder.queryParam("api_key", properties.tmdb().apiKey());
                    return builder.build(type, externalId);
                });
        if (bearer) request.header("Authorization", "Bearer " + properties.tmdb().accessToken());
        JsonNode body = request.retrieve().body(JsonNode.class);
        return new ArtworkCatalog(
                ArtworkProvider.TMDB,
                assets(body.path("posters"), "w500", locale),
                assets(body.path("backdrops"), "w780", locale, true)
        );
    }

    private List<ArtworkAsset> assets(JsonNode images, String previewSize, String locale) {
        return assets(images, previewSize, locale, false);
    }

    private List<ArtworkAsset> assets(
            JsonNode images,
            String previewSize,
            String locale,
            boolean textlessOnly
    ) {
        Map<String, RankedAsset> distinct = new LinkedHashMap<>();
        for (JsonNode image : images) {
            String path = text(image, "file_path");
            String language = text(image, "iso_639_1");
            if (path == null || !(language == null || language.equals(locale) || language.equals("en"))) continue;
            if (textlessOnly && language != null) continue;
            ArtworkAsset asset = new ArtworkAsset(
                    path,
                    IMAGE_BASE_URL + "/original" + path,
                    IMAGE_BASE_URL + '/' + previewSize + path,
                    integer(image, "width"),
                    integer(image, "height"),
                    language
            );
            RankedAsset ranked = new RankedAsset(
                    asset,
                    languageRank(language, locale),
                    image.path("vote_count").asInt(0),
                    image.path("vote_average").asDouble(0)
            );
            distinct.putIfAbsent(path, ranked);
        }
        return distinct.values().stream()
                .sorted(Comparator.comparingInt(RankedAsset::languageRank)
                        .thenComparing(Comparator.comparingInt(RankedAsset::voteCount).reversed())
                        .thenComparing(Comparator.comparingDouble(RankedAsset::voteAverage).reversed())
                        .thenComparing(item -> item.asset().key()))
                .map(RankedAsset::asset)
                .toList();
    }

    private int languageRank(String language, String locale) {
        if (locale.equals(language)) return 0;
        if ("en".equals(language)) return 1;
        return 2;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private Integer integer(JsonNode node, String field) {
        return node.path(field).isNumber() ? node.path(field).asInt() : null;
    }

    private record RankedAsset(ArtworkAsset asset, int languageRank, int voteCount, double voteAverage) {}
    private record CacheEntry(ArtworkCatalog catalog, Instant expiresAt) {}
}
