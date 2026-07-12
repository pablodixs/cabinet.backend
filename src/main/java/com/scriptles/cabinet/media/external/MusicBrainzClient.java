package com.scriptles.cabinet.media.external;

import com.scriptles.cabinet.media.config.ExternalApiProperties;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class MusicBrainzClient implements ExternalMediaProvider {
    private final RestClient.Builder restClientBuilder;
    private final ExternalApiProperties properties;
    private final AlbumCoverService albumCoverService;
    private long nextRequestAt;

    @Override
    public ExternalSource source() {
        return ExternalSource.MUSICBRAINZ;
    }

    @Override
    public boolean supports(MediaType mediaType) {
        return mediaType == MediaType.ALBUM;
    }

    @Override
    public List<ExternalMedia> search(MediaType mediaType, String query) {
        return search(mediaType, query, null, 0, 20);
    }

    @Override
    public List<ExternalMedia> search(MediaType mediaType, String query, String language, int offset, int limit) {
        JsonNode body = get("/release-group", query, offset, limit);
        List<ExternalMedia> results = new ArrayList<>();
        for (JsonNode item : body.path("release-groups")) {
            results.add(toMedia(item));
        }
        return results;
    }

    @Override
    public Optional<ExternalMedia> findById(MediaType mediaType, String externalId) {
        JsonNode body = get("/release-group/" + externalId, null, 0, 0);
        return body.isMissingNode() || body.isEmpty() ? Optional.empty() : Optional.of(toMedia(body));
    }

    private JsonNode get(String path, String query, int offset, int limit) {
        waitForRateLimit();
        try {
            return restClientBuilder.baseUrl(properties.musicbrainz().baseUrl()).build().get()
                    .uri(uriBuilder -> {
                        uriBuilder.path(path).queryParam("fmt", "json");
                        if (query != null) {
                            uriBuilder.queryParam("query", query)
                                    .queryParam("offset", offset)
                                    .queryParam("limit", limit);
                        }
                        return uriBuilder.build();
                    })
                    .header("User-Agent", properties.musicbrainz().userAgent())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 503) {
                throw new ExternalMediaRateLimitException("MusicBrainz rate limit exceeded", exception);
            }
            throw new ExternalMediaException(
                    "MusicBrainz responded with HTTP " + exception.getStatusCode().value(), exception
            );
        } catch (RestClientException exception) {
            throw new ExternalMediaException("Unable to communicate with MusicBrainz", exception);
        }
    }

    private synchronized void waitForRateLimit() {
        long delay = nextRequestAt - System.currentTimeMillis();
        if (delay > 0) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new ExternalMediaException("MusicBrainz request was interrupted", exception);
            }
        }
        nextRequestAt = System.currentTimeMillis() + 1_000;
    }

    private ExternalMedia toMedia(JsonNode node) {
        String id = text(node, "id");
        return new ExternalMedia(
                ExternalSource.MUSICBRAINZ, id, MediaType.ALBUM, text(node, "title"), null,
                text(node, "disambiguation"), albumCoverService.findCoverUrl(id),
                "https://musicbrainz.org/release-group/" + id, date(text(node, "first-release-date")),
                null, null, null, null, null, null, null, null, null, null, null, null,
                null, text(node, "primary-type"), null, artistNames(node.path("artist-credit"))
        );
    }

    private String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private String artistNames(JsonNode credits) {
        List<String> names = new ArrayList<>();
        for (JsonNode credit : credits) {
            String name = text(credit, "name");
            if (name == null) {
                name = text(credit.path("artist"), "name");
            }
            if (name != null) {
                names.add(name);
            }
        }
        return names.isEmpty() ? null : String.join(", ", names);
    }

    private LocalDate date(String value) {
        if (value == null) {
            return null;
        }
        try {
            if (value.matches("\\d{4}")) {
                return LocalDate.of(Integer.parseInt(value), 1, 1);
            }
            if (value.matches("\\d{4}-\\d{2}")) {
                return LocalDate.parse(value + "-01");
            }
            return LocalDate.parse(value);
        } catch (DateTimeParseException | NumberFormatException exception) {
            return null;
        }
    }
}
