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
public class GoogleBooksClient implements ExternalMediaProvider {
    private final RestClient.Builder restClientBuilder;
    private final ExternalApiProperties properties;

    @Override
    public ExternalSource source() {
        return ExternalSource.GOOGLE_BOOKS;
    }

    @Override
    public boolean supports(MediaType mediaType) {
        return mediaType == MediaType.BOOK;
    }

    @Override
    public List<ExternalMedia> search(MediaType mediaType, String query) {
        JsonNode body = get("/volumes", query);
        List<ExternalMedia> results = new ArrayList<>();
        for (JsonNode item : body.path("items")) {
            results.add(toMedia(item));
        }
        return results;
    }

    @Override
    public Optional<ExternalMedia> findById(MediaType mediaType, String externalId) {
        JsonNode body = get("/volumes/" + externalId, null);
        return body.isMissingNode() || body.isEmpty() ? Optional.empty() : Optional.of(toMedia(body));
    }

    private JsonNode get(String path, String query) {
        try {
            return restClientBuilder.baseUrl(properties.googleBooks().baseUrl()).build().get()
                    .uri(uriBuilder -> {
                        uriBuilder.path(path);
                        if (query != null) {
                            uriBuilder.queryParam("q", query);
                        }
                        return uriBuilder.build();
                    })
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException exception) {
            throw new ExternalMediaException("Falha ao consultar Google Books", exception);
        }
    }

    private ExternalMedia toMedia(JsonNode node) {
        JsonNode volume = node.path("volumeInfo");
        String id = text(node, "id");
        return new ExternalMedia(
                ExternalSource.GOOGLE_BOOKS, id, MediaType.BOOK, text(volume, "title"), null,
                text(volume, "description"), thumbnail(volume), text(volume, "infoLink"),
                publicationDate(text(volume, "publishedDate")), text(volume, "language"), null,
                isbn(volume, "ISBN_10"), isbn(volume, "ISBN_13"), integer(volume, "pageCount"),
                text(volume, "publisher"), null, null, null, null, null, null, null
        );
    }

    private String thumbnail(JsonNode volume) {
        String thumbnail = text(volume.path("imageLinks"), "thumbnail");
        return thumbnail == null ? null : thumbnail.replaceFirst("^http://", "https://");
    }

    private String isbn(JsonNode volume, String kind) {
        for (JsonNode identifier : volume.path("industryIdentifiers")) {
            if (kind.equals(text(identifier, "type"))) {
                return text(identifier, "identifier");
            }
        }
        return null;
    }

    private String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private Integer integer(JsonNode node, String field) {
        return node.path(field).isNumber() ? node.path(field).asInt() : null;
    }

    private LocalDate publicationDate(String value) {
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
