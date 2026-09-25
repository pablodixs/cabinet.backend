package com.scriptles.cabinet.media.external;

import com.scriptles.cabinet.media.config.OmdbProperties;
import com.scriptles.cabinet.media.enums.ExternalRatingMetric;
import com.scriptles.cabinet.media.enums.ExternalSource;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
public class OmdbClient {
    private final RestClient.Builder restClientBuilder;
    private final OmdbProperties properties;
    private final MeterRegistry meters;

    @Autowired
    public OmdbClient(RestClient.Builder restClientBuilder, OmdbProperties properties, MeterRegistry meters) {
        this.restClientBuilder = restClientBuilder;
        this.properties = properties;
        this.meters = meters;
    }

    public OmdbClient(RestClient.Builder restClientBuilder, OmdbProperties properties) {
        this(restClientBuilder, properties, new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
    }

    public boolean isConfigured() {
        return properties.configured();
    }

    public List<ExternalRatingValue> findRatings(String imdbId) {
        if (!isConfigured()) {
            throw new ExternalMediaException("Configure OMDB_API_KEY");
        }
        if (imdbId == null || !imdbId.matches("tt\\d{7,9}")) {
            throw new IllegalArgumentException("Invalid IMDb ID");
        }

        JsonNode body = get(imdbId);
        if (!"True".equalsIgnoreCase(text(body, "Response"))) {
            String error = text(body, "Error");
            if (error != null && (error.toLowerCase(Locale.ROOT).contains("api key")
                    || error.toLowerCase(Locale.ROOT).contains("request limit"))) {
                throw new ExternalMediaException("OMDb rejected the request: " + error);
            }
            return List.of();
        }

        List<ExternalRatingValue> ratings = new ArrayList<>();
        for (JsonNode rating : body.path("Ratings")) {
            mapRating(text(rating, "Source"), text(rating, "Value")).ifPresent(ratings::add);
        }

        if (ratings.stream().noneMatch(value -> value.metric() == ExternalRatingMetric.METASCORE)) {
            mapRating("Metacritic", text(body, "Metascore")).ifPresent(ratings::add);
        }
        if (ratings.stream().noneMatch(value -> value.metric() == ExternalRatingMetric.IMDB_RATING)) {
            mapRating("Internet Movie Database", text(body, "imdbRating")).ifPresent(ratings::add);
        }
        return List.copyOf(ratings);
    }

    private JsonNode get(String imdbId) {
        Timer.Sample sample = Timer.start(meters);
        meters.counter("cabinet.provider.requests", "provider", "OMDB", "operation", "rating_lookup").increment();
        String outcome = "success";
        try {
            return restClientBuilder.clone().baseUrl(properties.baseUrl()).build().get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/")
                            .queryParam("apikey", properties.apiKey())
                            .queryParam("i", imdbId)
                            .queryParam("plot", "short")
                            .queryParam("r", "json")
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            outcome = "failure";
            meters.counter("cabinet.provider.failures", "provider", "OMDB", "operation", "rating_lookup")
                    .increment();
            if (exception.getStatusCode().value() == 429) {
                meters.counter("cabinet.provider.rate_limited", "provider", "OMDB",
                        "operation", "rating_lookup").increment();
                throw new ExternalMediaRateLimitException("OMDb rate limit exceeded", exception);
            }
            throw new ExternalMediaException("OMDb responded with HTTP " + exception.getStatusCode().value(), exception);
        } catch (RestClientException exception) {
            outcome = "failure";
            meters.counter("cabinet.provider.failures", "provider", "OMDB", "operation", "rating_lookup")
                    .increment();
            throw new ExternalMediaException("Unable to query OMDb", exception);
        } finally {
            sample.stop(meters.timer("cabinet.provider.duration", "provider", "OMDB",
                    "operation", "rating_lookup", "outcome", outcome));
        }
    }

    private Optional<ExternalRatingValue> mapRating(String source, String rawValue) {
        if (source == null || rawValue == null || "N/A".equalsIgnoreCase(rawValue)) {
            return Optional.empty();
        }
        String normalizedSource = source.toLowerCase(Locale.ROOT);
        if (normalizedSource.contains("rotten tomatoes")) {
            return percentage(rawValue, ExternalSource.ROTTEN_TOMATOES, ExternalRatingMetric.TOMATOMETER);
        }
        if (normalizedSource.contains("metacritic")) {
            return fraction(rawValue, ExternalSource.METACRITIC, ExternalRatingMetric.METASCORE, 100);
        }
        if (normalizedSource.contains("internet movie database") || normalizedSource.equals("imdb")) {
            return fraction(rawValue, ExternalSource.IMDB, ExternalRatingMetric.IMDB_RATING, 10);
        }
        return Optional.empty();
    }

    private Optional<ExternalRatingValue> percentage(
            String rawValue,
            ExternalSource source,
            ExternalRatingMetric metric
    ) {
        String number = rawValue.replace("%", "").trim();
        return number(number).map(value -> new ExternalRatingValue(source, metric, value, 100, rawValue));
    }

    private Optional<ExternalRatingValue> fraction(
            String rawValue,
            ExternalSource source,
            ExternalRatingMetric metric,
            int defaultScale
    ) {
        String[] parts = rawValue.split("/");
        Optional<Double> value = number(parts[0].trim());
        if (value.isEmpty()) {
            return Optional.empty();
        }
        int scale = defaultScale;
        if (parts.length > 1) {
            try {
                scale = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException ignored) {
                // Keep the source's documented scale.
            }
        }
        return Optional.of(new ExternalRatingValue(source, metric, value.get(), scale, rawValue));
    }

    private Optional<Double> number(String value) {
        try {
            return Optional.of(Double.parseDouble(value));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }
}
