package com.scriptles.cabinet.media.external;

import com.scriptles.cabinet.media.config.ExternalApiProperties;
import com.scriptles.cabinet.media.enums.CreditRole;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.http.converter.HttpMessageConversionException;
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
public class GoogleBooksClient implements ExternalMediaProvider {
    private static final String SEARCH_FIELDS = "items(id,volumeInfo(title,authors,description,imageLinks/thumbnail,infoLink,publishedDate,language,industryIdentifiers,pageCount,publisher)),totalItems";
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
        return search(mediaType, query, null, 0, 20);
    }

    @Override
    public List<ExternalMedia> search(MediaType mediaType, String query, String language) {
        return search(mediaType, query, language, 0, 20);
    }

    @Override
    public List<ExternalMedia> search(MediaType mediaType, String query, String language, int offset, int limit) {
        JsonNode body = get("/volumes", query, language, offset, limit);
        List<ExternalMedia> results = new ArrayList<>();
        for (JsonNode item : body.path("items")) {
            results.add(toMedia(item));
        }
        return results;
    }

    @Override
    public Optional<ExternalMedia> findById(MediaType mediaType, String externalId) {
        JsonNode body = get("/volumes/" + externalId, null, null, 0, 0);
        return body.isMissingNode() || body.isEmpty() ? Optional.empty() : Optional.of(toMedia(body));
    }

    private JsonNode get(String path, String query, String language, int offset, int limit) {
        if (properties.googleBooks().apiKey() == null || properties.googleBooks().apiKey().isBlank()) {
            throw new ExternalMediaException("Configure GOOGLE_BOOKS_API_KEY");
        }
        try {
            return restClientBuilder.baseUrl(properties.googleBooks().baseUrl()).build().get()
                    .uri(uriBuilder -> {
                        uriBuilder.path(path);
                        uriBuilder.queryParam("key", properties.googleBooks().apiKey());
                        if (query != null) {
                            uriBuilder.queryParam("q", query);
                            uriBuilder.queryParam("printType", "books");
                            uriBuilder.queryParam("startIndex", offset);
                            uriBuilder.queryParam("maxResults", limit);
                            uriBuilder.queryParam("fields", SEARCH_FIELDS);
                            if (language != null) {
                                uriBuilder.queryParam("langRestrict", language.substring(0, 2));
                            }
                        }
                        return uriBuilder.build();
                    })
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 429) {
                throw new ExternalMediaRateLimitException("Google Books rate limit exceeded", exception);
            }
            throw new ExternalMediaException(
                    "Google Books responded with HTTP " + exception.getStatusCode().value(), exception
            );
        } catch (HttpMessageConversionException exception) {
            throw new ExternalMediaException("Google Books response cannot be processed", exception);
        } catch (RestClientException exception) {
            throw new ExternalMediaException("Unable to communicate with Google Books", exception);
        }
    }

    private ExternalMedia toMedia(JsonNode node) {
        JsonNode volume = node.path("volumeInfo");
        JsonNode authors = volume.path("authors");
        List<ExternalMedia.ExternalCredit> credits = authorCredits(authors);
        String id = text(node, "id");
        return new ExternalMedia(
                ExternalSource.GOOGLE_BOOKS, id, MediaType.BOOK, text(volume, "title"), null,
                text(volume, "description"), null, thumbnail(volume), text(volume, "infoLink"),
                null, publicationDate(text(volume, "publishedDate")), text(volume, "language"), null,
                isbn(volume, "ISBN_10"), isbn(volume, "ISBN_13"), integer(volume, "pageCount"),
                text(volume, "publisher"), null, null, null, null, null, null, null, null, null, null, null,
                names(authors), null, null, List.of(), List.of(), List.of(), credits
        );
    }

    private List<ExternalMedia.ExternalCredit> authorCredits(JsonNode authors) {
        List<ExternalMedia.ExternalCredit> credits = new ArrayList<>();
        int position = 0;
        for (JsonNode author : authors) {
            String name = author.asText(null);
            if (name != null && !name.isBlank()) {
                credits.add(new ExternalMedia.ExternalCredit(
                        null,
                        name,
                        CreditRole.AUTHOR,
                        null,
                        position++,
                        null,
                        ExternalSource.GOOGLE_BOOKS
                ));
            }
        }
        return List.copyOf(credits);
    }

    private String thumbnail(JsonNode volume) {
        String thumbnail = text(volume.path("imageLinks"), "thumbnail");
        return thumbnail == null ? null : thumbnail.replaceFirst("^http://", "https://");
    }

    private String names(JsonNode values) {
        List<String> names = new ArrayList<>();
        for (JsonNode value : values) {
            String name = value.asText(null);
            if (name != null && !name.isBlank()) {
                names.add(name);
            }
        }
        return names.isEmpty() ? null : String.join(", ", names);
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
