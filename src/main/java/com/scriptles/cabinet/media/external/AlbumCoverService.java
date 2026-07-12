package com.scriptles.cabinet.media.external;

import com.scriptles.cabinet.media.config.ExternalApiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class AlbumCoverService {
    private static final String COVER_ART_BASE_URL = "https://coverartarchive.org";

    private final RestClient.Builder restClientBuilder;
    private final ExternalApiProperties properties;
    private final Map<String, Optional<String>> cache = new ConcurrentHashMap<>();

    public String findCoverUrl(String releaseGroupId) {
        if (releaseGroupId == null) {
            return null;
        }
        return cache.computeIfAbsent(releaseGroupId, this::resolveCoverUrl).orElse(null);
    }

    private Optional<String> resolveCoverUrl(String releaseGroupId) {
        return findCoverArtArchiveUrl(releaseGroupId)
                .or(() -> findTheAudioDbUrl(releaseGroupId));
    }

    private Optional<String> findCoverArtArchiveUrl(String releaseGroupId) {
        try {
            JsonNode body = restClientBuilder.baseUrl(COVER_ART_BASE_URL).build().get()
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
            JsonNode body = restClientBuilder.baseUrl(properties.theAudioDb().baseUrl()).build().get()
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
}
