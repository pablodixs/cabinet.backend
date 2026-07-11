package com.scriptles.cabinet.media.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.scriptles.cabinet.media.config.ExternalApiProperties;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class TmdbClient implements ExternalMediaProvider {
    private static final String IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w500";

    private final RestClient.Builder restClientBuilder;
    private final ExternalApiProperties properties;

    @Override
    public ExternalSource source() {
        return ExternalSource.TMDB;
    }

    @Override
    public boolean supports(MediaType mediaType) {
        return mediaType == MediaType.MOVIE || mediaType == MediaType.SERIES;
    }

    @Override
    public List<ExternalMedia> search(MediaType mediaType, String query) {
        JsonNode body = get(searchPath(mediaType), query);
        List<ExternalMedia> results = new ArrayList<>();
        for (JsonNode item : body.path("results")) {
            results.add(toMedia(item, mediaType, false));
        }
        return results;
    }

    @Override
    public Optional<ExternalMedia> findById(MediaType mediaType, String externalId) {
        JsonNode body = get(detailPath(mediaType, externalId), null);
        return body.isMissingNode() || body.isEmpty() ? Optional.empty() : Optional.of(toMedia(body, mediaType, true));
    }

    private JsonNode get(String path, String query) {
        if (properties.tmdb().apiKey() == null || properties.tmdb().apiKey().isBlank()) {
            throw new ExternalMediaException("TMDB_API_KEY nao foi configurada");
        }

        try {
            return restClientBuilder.baseUrl(properties.tmdb().baseUrl()).build().get()
                    .uri(uriBuilder -> {
                        uriBuilder.path(path).queryParam("api_key", properties.tmdb().apiKey());
                        if (query != null) {
                            uriBuilder.queryParam("query", query);
                        }
                        return uriBuilder.build();
                    })
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException exception) {
            throw new ExternalMediaException("Falha ao consultar TMDB", exception);
        }
    }

    private ExternalMedia toMedia(JsonNode node, MediaType type, boolean detailed) {
        boolean movie = type == MediaType.MOVIE;
        String title = text(node, movie ? "title" : "name");
        String originalTitle = text(node, movie ? "original_title" : "original_name");
        LocalDate releaseDate = date(text(node, movie ? "release_date" : "first_air_date"));
        String id = text(node, "id");

        return new ExternalMedia(
                ExternalSource.TMDB, id, type, title, originalTitle, text(node, "overview"),
                imageUrl(text(node, "poster_path")),
                "https://www.themoviedb.org/%s/%s".formatted(movie ? "movie" : "tv", id),
                releaseDate, text(node, "original_language"), countryCode(node), null, null, null, null,
                detailed && movie ? integer(node, "runtime") : null,
                detailed && movie ? longValue(node, "budget") : null,
                detailed && movie ? longValue(node, "revenue") : null,
                detailed && !movie ? text(node, "status") : null,
                detailed && !movie ? integer(node, "number_of_seasons") : null,
                detailed && !movie ? integer(node, "number_of_episodes") : null,
                detailed && !movie ? date(text(node, "last_air_date")) : null
        );
    }

    private String searchPath(MediaType type) {
        return type == MediaType.MOVIE ? "/search/movie" : "/search/tv";
    }

    private String detailPath(MediaType type, String externalId) {
        return (type == MediaType.MOVIE ? "/movie/" : "/tv/") + externalId;
    }

    private String countryCode(JsonNode node) {
        JsonNode countries = node.path("production_countries");
        return countries.isArray() && !countries.isEmpty() ? text(countries.get(0), "iso_3166_1") : null;
    }

    private String imageUrl(String path) {
        return path == null ? null : IMAGE_BASE_URL + path;
    }

    private String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private Integer integer(JsonNode node, String field) {
        return node.path(field).isNumber() ? node.path(field).asInt() : null;
    }

    private Long longValue(JsonNode node, String field) {
        return node.path(field).isNumber() ? node.path(field).asLong() : null;
    }

    private LocalDate date(String value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }
}
