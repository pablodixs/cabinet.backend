package com.scriptles.cabinet.media.external;

import com.scriptles.cabinet.media.config.ExternalApiProperties;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaRelationType;
import com.scriptles.cabinet.media.enums.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WikidataClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(WikidataClient.class);
    private static final Duration HIT_TTL = Duration.ofHours(6);
    private static final Duration MISS_TTL = Duration.ofMinutes(15);
    private static final Duration STALE_TTL = Duration.ofDays(7);
    private static final int MAX_CACHE_ENTRIES = 2_000;

    private final RestClient.Builder restClientBuilder;
    private final ExternalApiProperties properties;
    private final Clock clock;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final Map<String, RelationCacheEntry> relationCache = new ConcurrentHashMap<>();

    @Autowired
    public WikidataClient(RestClient.Builder restClientBuilder, ExternalApiProperties properties) {
        this(restClientBuilder, properties, Clock.systemUTC());
    }

    WikidataClient(RestClient.Builder restClientBuilder, ExternalApiProperties properties, Clock clock) {
        this.restClientBuilder = restClientBuilder;
        this.properties = properties;
        this.clock = clock;
    }

    public Optional<WikidataEnrichment> find(
            ExternalSource source,
            MediaType type,
            String externalId,
            String language
    ) {
        String property = property(source, type);
        if (property == null) {
            return Optional.empty();
        }
        String key = property + ':' + externalId + ':' + normalizedLanguage(language);
        return cached(key, "?item wdt:%s %s .".formatted(property, sparqlLiteral(externalId)), language);
    }

    public Optional<WikidataEnrichment> findBook(
            String googleBooksId,
            String isbn13,
            String isbn10,
            String language
    ) {
        String subjectClause = bookSubjectClause(googleBooksId, isbn13, isbn10);
        if (subjectClause == null) {
            return Optional.empty();
        }
        String key = "book:%s:%s:%s:%s".formatted(
                googleBooksId,
                normalizedIsbn(isbn13),
                normalizedIsbn(isbn10),
                normalizedLanguage(language)
        );
        return cached(key, subjectClause, language).map(value ->
                value.canonicalWorkWikidataId() != null
                        ? value
                        : new WikidataEnrichment(
                        value.wikidataId(),
                        value.wikidataId(),
                        value.logoUrl(),
                        value.genres()
                )
        );
    }

    public Optional<WikidataEnrichment> findById(String wikidataId, String language) {
        if (!validQid(wikidataId)) {
            return Optional.empty();
        }
        return cached(
                "qid:" + wikidataId + ':' + normalizedLanguage(language),
                "BIND(wd:%s AS ?item)".formatted(wikidataId),
                language
        );
    }

    public WikidataRelations findRelations(RelationLookup lookup) {
        String anchorClause = relationAnchorClause(lookup);
        if (anchorClause == null || !supportedRelationType(lookup.type())) {
            return new WikidataRelations(false, List.of());
        }

        String key = "relations:%s:%s:%s:%s:%s:%s:%d".formatted(
                lookup.source(),
                lookup.type(),
                lookup.externalId(),
                lookup.wikidataId(),
                normalizedIsbn(lookup.isbn13()) + ':' + normalizedIsbn(lookup.isbn10()),
                normalizedLanguage(lookup.language()),
                lookup.maxResults()
        );
        Instant now = Instant.now(clock);
        RelationCacheEntry cached = relationCache.get(key);
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return new WikidataRelations(false, cached.items());
        }

        Optional<List<RelatedMedia>> queried = queryRelations(anchorClause, lookup);
        if (queried.isPresent()) {
            List<RelatedMedia> items = queried.get();
            Duration ttl = items.isEmpty() ? MISS_TTL : HIT_TTL;
            RelationCacheEntry entry = new RelationCacheEntry(
                    items,
                    now.plus(ttl),
                    now.plus(STALE_TTL)
            );
            pruneRelationCache(now);
            relationCache.put(key, entry);
            return new WikidataRelations(false, items);
        }

        if (cached != null && cached.staleUntil().isAfter(now)) {
            return new WikidataRelations(true, cached.items());
        }
        return new WikidataRelations(true, List.of());
    }

    private Optional<WikidataEnrichment> cached(String key, String subjectClause, String language) {
        Instant now = Instant.now(clock);
        CacheEntry cached = cache.get(key);
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.value();
        }

        EnrichmentQueryResult queried = query(subjectClause, language);
        if (!queried.completed()) {
            if (cached != null && cached.value().isPresent() && cached.staleUntil().isAfter(now)) {
                return cached.value();
            }
            return Optional.empty();
        }

        Optional<WikidataEnrichment> value = queried.value();
        Duration ttl = value.isPresent() ? HIT_TTL : MISS_TTL;
        if (cache.size() >= MAX_CACHE_ENTRIES) {
            cache.entrySet().removeIf(entry -> entry.getValue().staleUntil().isBefore(now));
            if (cache.size() >= MAX_CACHE_ENTRIES) {
                cache.clear();
            }
        }
        cache.put(key, new CacheEntry(
                value,
                now.plus(ttl),
                value.isPresent() ? now.plus(STALE_TTL) : now.plus(MISS_TTL)
        ));
        return value;
    }

    private EnrichmentQueryResult query(String subjectClause, String language) {
        String languageCode = normalizedLanguage(language);
        String query = """
                SELECT ?item ?work ?genre ?genreLabel ?logo WHERE {
                  %s
                  OPTIONAL { ?item wdt:P629 ?work. }
                  OPTIONAL { ?item wdt:P136 ?genre. }
                  OPTIONAL { ?item wdt:P154 ?logo. }
                  SERVICE wikibase:label { bd:serviceParam wikibase:language \"%s,en\". }
                }
                """.formatted(subjectClause, languageCode);
        try {
            JsonNode body = execute(query);
            return new EnrichmentQueryResult(true, parse(body));
        } catch (RuntimeException exception) {
            LOGGER.warn("Wikidata enrichment query failed: {}", exception.getMessage());
            return new EnrichmentQueryResult(false, Optional.empty());
        }
    }

    private Optional<List<RelatedMedia>> queryRelations(String anchorClause, RelationLookup lookup) {
        String query = """
                SELECT ?related ?relationType
                       (SAMPLE(?relatedLabel) AS ?title)
                       (MIN(?date) AS ?releaseDate)
                       (SAMPLE(?image) AS ?cover)
                       (SAMPLE(?tmdbMovieId) AS ?movieId)
                       (SAMPLE(?tmdbTvId) AS ?seriesId)
                       (SAMPLE(?musicBrainzReleaseGroupId) AS ?albumId)
                       (SAMPLE(?googleBooksCandidate) AS ?bookId)
                WHERE {
                  %s
                  %s
                  OPTIONAL { ?related wdt:P577 ?date. }
                  OPTIONAL { ?related wdt:P18 ?image. }
                  OPTIONAL { ?related wdt:P4947 ?tmdbMovieId. }
                  OPTIONAL { ?related wdt:P4983 ?tmdbTvId. }
                  OPTIONAL { ?related wdt:P436 ?musicBrainzReleaseGroupId. }
                  OPTIONAL {
                    { ?related wdt:P675 ?googleBooksCandidate. }
                    UNION
                    { ?bookEdition wdt:P629 ?related; wdt:P675 ?googleBooksCandidate. }
                  }
                  SERVICE wikibase:label {
                    bd:serviceParam wikibase:language \"%s,en\".
                    ?related rdfs:label ?relatedLabel.
                  }
                }
                GROUP BY ?related ?relationType
                ORDER BY ?relationType ?releaseDate ?title
                LIMIT %d
                """.formatted(
                anchorClause,
                relationshipClause(lookup.type()),
                normalizedLanguage(lookup.language()),
                lookup.maxResults()
        );
        try {
            return Optional.of(parseRelations(execute(query)));
        } catch (RuntimeException exception) {
            LOGGER.warn("Wikidata relation query failed: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    private JsonNode execute(String query) {
        String separator = properties.wikidata().sparqlUrl().contains("?") ? "&" : "?";
        URI uri = URI.create(properties.wikidata().sparqlUrl()
                + separator
                + "query="
                + UriUtils.encodeQueryParam(query, StandardCharsets.UTF_8)
                + "&format=json");
        return restClientBuilder.baseUrl(properties.wikidata().sparqlUrl()).build().get()
                .uri(uri)
                .header("User-Agent", properties.wikidata().userAgent())
                .retrieve()
                .body(JsonNode.class);
    }

    private Optional<WikidataEnrichment> parse(JsonNode body) {
        JsonNode bindings = body.path("results").path("bindings");
        if (!bindings.isArray() || bindings.isEmpty()) {
            return Optional.empty();
        }
        String wikidataId = qid(value(bindings.get(0), "item"));
        String canonicalWorkWikidataId = qid(value(bindings.get(0), "work"));
        String logoUrl = value(bindings.get(0), "logo");
        Map<String, ExternalMedia.ExternalGenre> genres = new LinkedHashMap<>();
        for (JsonNode binding : bindings) {
            String genreUrl = value(binding, "genre");
            String name = value(binding, "genreLabel");
            if (genreUrl != null && name != null) {
                String id = qid(genreUrl);
                genres.putIfAbsent(id, new ExternalMedia.ExternalGenre(id, name, ExternalSource.WIKIDATA));
            }
        }
        return Optional.of(new WikidataEnrichment(
                wikidataId,
                canonicalWorkWikidataId,
                secure(logoUrl),
                new ArrayList<>(genres.values())
        ));
    }

    private List<RelatedMedia> parseRelations(JsonNode body) {
        JsonNode bindings = body.path("results").path("bindings");
        if (!bindings.isArray() || bindings.isEmpty()) {
            return List.of();
        }

        Map<String, RelatedMedia> related = new LinkedHashMap<>();
        for (JsonNode binding : bindings) {
            String wikidataId = qid(value(binding, "related"));
            String relationValue = value(binding, "relationType");
            String title = value(binding, "title");
            if (!validQid(wikidataId) || relationValue == null || title == null) {
                continue;
            }

            MediaRelationType relationType;
            try {
                relationType = MediaRelationType.valueOf(relationValue);
            } catch (IllegalArgumentException exception) {
                continue;
            }

            String movieId = value(binding, "movieId");
            String seriesId = value(binding, "seriesId");
            String albumId = value(binding, "albumId");
            String bookId = value(binding, "bookId");
            ProviderIdentity provider = providerIdentity(
                    relationType,
                    movieId,
                    seriesId,
                    albumId,
                    bookId
            );
            String key = relationType + ":" + wikidataId;
            related.putIfAbsent(key, new RelatedMedia(
                    relationType,
                    provider.type(),
                    title,
                    date(value(binding, "releaseDate")),
                    secure(value(binding, "cover")),
                    wikidataId,
                    provider.source(),
                    provider.externalId(),
                    "https://www.wikidata.org/wiki/" + wikidataId
            ));
        }
        return new ArrayList<>(related.values());
    }

    private ProviderIdentity providerIdentity(
            MediaRelationType relationType,
            String movieId,
            String seriesId,
            String albumId,
            String bookId
    ) {
        return switch (relationType) {
            case ADAPTATION_OF -> new ProviderIdentity(
                    MediaType.BOOK,
                    bookId == null ? null : ExternalSource.GOOGLE_BOOKS,
                    bookId
            );
            case SOUNDTRACK, RE_RECORDING_OF, RE_RECORDED_AS -> new ProviderIdentity(
                    MediaType.ALBUM,
                    albumId == null ? null : ExternalSource.MUSICBRAINZ,
                    albumId
            );
            case ADAPTED_AS, SOUNDTRACK_OF -> {
                if (seriesId != null && movieId == null) {
                    yield new ProviderIdentity(MediaType.SERIES, ExternalSource.TMDB, seriesId);
                }
                yield new ProviderIdentity(
                        MediaType.MOVIE,
                        movieId == null ? null : ExternalSource.TMDB,
                        movieId
                );
            }
        };
    }

    private String relationAnchorClause(RelationLookup lookup) {
        String itemClause;
        if (validQid(lookup.wikidataId())) {
            itemClause = "BIND(wd:%s AS ?item)".formatted(lookup.wikidataId());
        } else if (lookup.type() == MediaType.BOOK) {
            itemClause = bookSubjectClause(lookup.externalId(), lookup.isbn13(), lookup.isbn10());
        } else {
            String property = property(lookup.source(), lookup.type());
            if (property == null) {
                return null;
            }
            itemClause = "?item wdt:%s %s .".formatted(property, sparqlLiteral(lookup.externalId()));
        }

        if (itemClause == null) {
            return null;
        }
        if (lookup.type() == MediaType.BOOK) {
            return itemClause + "\nOPTIONAL { ?item wdt:P629 ?canonicalWork. }\n"
                    + "BIND(COALESCE(?canonicalWork, ?item) AS ?anchor)";
        }
        return itemClause + "\nBIND(?item AS ?anchor)";
    }

    private String bookSubjectClause(String googleBooksId, String isbn13, String isbn10) {
        List<String> alternatives = new ArrayList<>();
        if (googleBooksId != null && !googleBooksId.isBlank()) {
            alternatives.add("{ ?item wdt:P675 %s. BIND(0 AS ?matchPriority) }"
                    .formatted(sparqlLiteral(googleBooksId)));
        }
        addIsbnAlternative(alternatives, isbn13, "P212", 1);
        addIsbnAlternative(alternatives, isbn10, "P957", 2);
        if (alternatives.isEmpty()) {
            return null;
        }
        return """
                {
                  SELECT ?item WHERE {
                    %s
                  }
                  ORDER BY ?matchPriority
                  LIMIT 1
                }
                """.formatted(String.join("\nUNION\n", alternatives));
    }

    private void addIsbnAlternative(List<String> alternatives, String isbn, String property, int priority) {
        String normalized = normalizedIsbn(isbn);
        if (normalized == null) {
            return;
        }
        alternatives.add("""
                {
                  ?item wdt:%s ?isbnCandidate.
                  FILTER(REPLACE(UCASE(STR(?isbnCandidate)), \"[- ]\", \"\") = %s)
                  BIND(%d AS ?matchPriority)
                }
                """.formatted(property, sparqlLiteral(normalized), priority));
    }

    private String relationshipClause(MediaType type) {
        return switch (type) {
            case BOOK -> """
                    ?related wdt:P144 ?anchor.
                    BIND(\"ADAPTED_AS\" AS ?relationType)
                    """;
            case MOVIE, SERIES -> """
                    {
                      ?anchor wdt:P144 ?related.
                      BIND(\"ADAPTATION_OF\" AS ?relationType)
                    }
                    UNION
                    {
                      ?anchor wdt:P406 ?related.
                      BIND(\"SOUNDTRACK\" AS ?relationType)
                    }
                    """;
            case ALBUM -> """
                    ?related wdt:P406 ?anchor.
                    BIND(\"SOUNDTRACK_OF\" AS ?relationType)
                    """;
            default -> throw new IllegalArgumentException("Relations are unavailable for type " + type);
        };
    }

    private boolean supportedRelationType(MediaType type) {
        return type == MediaType.BOOK
                || type == MediaType.MOVIE
                || type == MediaType.SERIES
                || type == MediaType.ALBUM;
    }

    private String property(ExternalSource source, MediaType type) {
        if (source == ExternalSource.TMDB && type == MediaType.MOVIE) return "P4947";
        if (source == ExternalSource.TMDB && type == MediaType.SERIES) return "P4983";
        if (source == ExternalSource.MUSICBRAINZ && type == MediaType.ALBUM) return "P436";
        if (source == ExternalSource.MUSICBRAINZ && type == MediaType.TRACK) return "P4404";
        if (source == ExternalSource.GOOGLE_BOOKS && type == MediaType.BOOK) return "P675";
        return null;
    }

    private void pruneRelationCache(Instant now) {
        if (relationCache.size() < MAX_CACHE_ENTRIES) {
            return;
        }
        relationCache.entrySet().removeIf(entry -> entry.getValue().staleUntil().isBefore(now));
        if (relationCache.size() >= MAX_CACHE_ENTRIES) {
            relationCache.clear();
        }
    }

    private boolean validQid(String value) {
        return value != null && value.matches("^Q[1-9]\\d*$");
    }

    private String sparqlLiteral(String value) {
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    private String value(JsonNode binding, String field) {
        return binding.path(field).path("value").asText(null);
    }

    private String qid(String value) {
        return value == null ? null : value.substring(value.lastIndexOf('/') + 1);
    }

    private String secure(String value) {
        return value == null ? null : value.replaceFirst("^http://", "https://");
    }

    private String normalizedLanguage(String language) {
        return language == null ? "en" : language.substring(0, 2);
    }

    private String normalizedIsbn(String isbn) {
        if (isbn == null || isbn.isBlank()) {
            return null;
        }
        return isbn.replace("-", "").replace(" ", "").toUpperCase();
    }

    private LocalDate date(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.startsWith("+") ? value.substring(1) : value;
        try {
            return LocalDate.parse(normalized.substring(0, 10));
        } catch (DateTimeParseException | IndexOutOfBoundsException exception) {
            return null;
        }
    }

    public record WikidataEnrichment(
            String wikidataId,
            String canonicalWorkWikidataId,
            String logoUrl,
            List<ExternalMedia.ExternalGenre> genres
    ) {
    }

    public record RelationLookup(
            ExternalSource source,
            MediaType type,
            String externalId,
            String wikidataId,
            String isbn13,
            String isbn10,
            String language,
            int maxResults
    ) {
    }

    public record WikidataRelations(boolean incomplete, List<RelatedMedia> items) {
    }

    public record RelatedMedia(
            MediaRelationType relationType,
            MediaType type,
            String title,
            LocalDate releaseDate,
            String coverUrl,
            String wikidataId,
            ExternalSource providerSource,
            String providerExternalId,
            String externalUrl
    ) {
    }

    private record ProviderIdentity(MediaType type, ExternalSource source, String externalId) {
    }

    private record EnrichmentQueryResult(boolean completed, Optional<WikidataEnrichment> value) {
    }

    private record CacheEntry(
            Optional<WikidataEnrichment> value,
            Instant expiresAt,
            Instant staleUntil
    ) {
    }

    private record RelationCacheEntry(List<RelatedMedia> items, Instant expiresAt, Instant staleUntil) {
    }
}
