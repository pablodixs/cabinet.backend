package com.scriptles.cabinet.media.external;

import com.scriptles.cabinet.media.config.ExternalApiProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.AwardDatePrecision;
import com.scriptles.cabinet.media.enums.AwardResult;
import com.scriptles.cabinet.media.enums.MediaRelationType;
import com.scriptles.cabinet.media.enums.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.util.UriUtils;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class WikidataClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(WikidataClient.class);
    private static final Duration HIT_TTL = Duration.ofHours(6);
    private static final Duration MISS_TTL = Duration.ofMinutes(15);
    private static final Duration STALE_TTL = Duration.ofDays(7);
    private static final Duration MAX_RATE_LIMIT_BACKOFF = Duration.ofSeconds(30);
    private static final int MAX_RATE_LIMIT_RETRIES = 2;
    private static final int MAX_TRANSIENT_RETRIES = 1;
    private static final int MAX_CACHE_ENTRIES = 2_000;

    private final RestClient.Builder restClientBuilder;
    private final ExternalApiProperties.Wikidata wikidata;
    private final Clock clock;
    private final MeterRegistry meters;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final Map<String, RelationCacheEntry> relationCache = new ConcurrentHashMap<>();

    @Autowired
    public WikidataClient(
            @Qualifier("wikidataRestClientBuilder") RestClient.Builder restClientBuilder,
            ExternalApiProperties properties,
            MeterRegistry meters
    ) {
        this(restClientBuilder, properties, Clock.systemUTC(), meters);
    }

    WikidataClient(RestClient.Builder restClientBuilder, ExternalApiProperties properties, Clock clock) {
        this(restClientBuilder, properties, clock, new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
    }

    private WikidataClient(RestClient.Builder restClientBuilder, ExternalApiProperties properties, Clock clock,
                           MeterRegistry meters) {
        this.restClientBuilder = restClientBuilder;
        this.wikidata = properties.wikidata() == null
                ? ExternalApiProperties.Wikidata.defaults()
                : properties.wikidata();
        this.clock = clock;
        this.meters = meters;
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

    public WikidataAwards findAwards(String wikidataId) {
        if (!validQid(wikidataId)) {
            return new WikidataAwards(false, List.of());
        }

        String candidateQuery = """
                SELECT DISTINCT ?award WHERE {
                  {
                    wd:%s p:P166 ?statement.
                    ?statement ps:P166 ?award.
                  }
                  UNION
                  {
                    wd:%s p:P1411 ?statement.
                    ?statement ps:P1411 ?award.
                  }
                }
                """.formatted(wikidataId, wikidataId);
        try {
            List<String> candidateAwardQids = awardQids(execute(candidateQuery));
            if (candidateAwardQids.isEmpty()) {
                return new WikidataAwards(false, List.of());
            }
            List<String> relevantAwardQids = awardQids(execute(awardClassificationQuery(candidateAwardQids)));
            if (relevantAwardQids.isEmpty()) {
                return new WikidataAwards(false, List.of());
            }
            return new WikidataAwards(false, parseAwards(execute(awardDetailsQuery(
                    wikidataId,
                    relevantAwardQids
            ))));
        } catch (RuntimeException exception) {
            logFailure("awards", wikidataId, exception);
            return new WikidataAwards(true, List.of());
        }
    }

    private String awardClassificationQuery(List<String> candidateAwardQids) {
        return """
                SELECT DISTINCT ?award WHERE {
                  hint:Query hint:optimizer "None".
                  VALUES ?award { %s }
                  VALUES ?root { wd:Q4220917 wd:Q1407225 wd:Q378427 wd:Q1364556 }
                  ?award (wdt:P31|wdt:P279)* ?root.
                  hint:Prior hint:gearing "forward".
                }
                """.formatted(awardValues(candidateAwardQids));
    }

    private String awardDetailsQuery(String wikidataId, List<String> candidateAwardQids) {
        String query = """
                SELECT ?statement ?result ?award ?awardLabel
                       ?program ?programLabel ?ceremony ?ceremonyLabel
                       ?date ?datePrecision ?work ?workLabel
                WHERE {
                  BIND(wd:%s AS ?item)
                  VALUES ?award { %s }
                  {
                    {
                      ?item p:P166 ?statement.
                      ?statement ps:P166 ?award.
                      BIND("WIN" AS ?result)
                    }
                    UNION
                    {
                      ?item p:P1411 ?statement.
                      ?statement ps:P1411 ?award.
                      BIND("NOMINATION" AS ?result)
                    }
                  }

                  OPTIONAL {
                    ?award wdt:P31 ?directProgram.
                    ?directProgram wdt:P31 wd:Q107655869.
                  }
                  OPTIONAL {
                    ?statement pq:P805 ?ceremony.
                    OPTIONAL { ?ceremony wdt:P1269 ?ceremonyProgram. }
                  }
                  BIND(COALESCE(?directProgram, ?ceremonyProgram) AS ?program)
                  OPTIONAL {
                    ?statement psv:P585 ?dateValue.
                    ?dateValue wikibase:timeValue ?date;
                               wikibase:timePrecision ?datePrecision.
                  }
                  OPTIONAL { ?statement pq:P1686 ?work. }
                  SERVICE wikibase:label {
                    bd:serviceParam wikibase:language "pt,en".
                    ?award rdfs:label ?awardLabel.
                    ?program rdfs:label ?programLabel.
                    ?ceremony rdfs:label ?ceremonyLabel.
                    ?work rdfs:label ?workLabel.
                  }
                }
                ORDER BY ?statement
                """.formatted(wikidataId, awardValues(candidateAwardQids));
        return query;
    }

    private String awardValues(List<String> awardQids) {
        return awardQids.stream()
                .map(qid -> "wd:" + qid)
                .collect(Collectors.joining(" "));
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
            logFailure("enrichment", null, exception);
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
            logFailure("relation", lookup.wikidataId(), exception);
            return Optional.empty();
        }
    }

    private JsonNode execute(String query) {
        String separator = wikidata.sparqlUrl().contains("?") ? "&" : "?";
        URI uri = URI.create(wikidata.sparqlUrl()
                + separator
                + "query="
                + UriUtils.encodeQueryParam(query, StandardCharsets.UTF_8)
                + "&format=json");
        for (int attempt = 0; ; attempt++) {
            Timer.Sample sample = Timer.start(meters);
            meters.counter("cabinet.provider.requests", "provider", "WIKIDATA", "operation", "sparql").increment();
            String outcome = "success";
            try {
                return restClientBuilder.clone().baseUrl(wikidata.sparqlUrl()).build().get()
                        .uri(uri)
                        .header("User-Agent", wikidata.userAgent())
                        .retrieve()
                        .body(JsonNode.class);
            } catch (RestClientResponseException exception) {
                outcome = "failure";
                meters.counter("cabinet.provider.failures", "provider", "WIKIDATA", "operation", "sparql")
                        .increment();
                if (exception.getStatusCode().value() == 429) {
                    meters.counter("cabinet.provider.rate_limited", "provider", "WIKIDATA",
                            "operation", "sparql").increment();
                }
                int maxRetries = maxRetries(exception.getStatusCode());
                if (attempt >= maxRetries) {
                    throw exception;
                }
                waitBeforeRetry(exception, attempt);
            } catch (ResourceAccessException exception) {
                outcome = "failure";
                meters.counter("cabinet.provider.failures", "provider", "WIKIDATA", "operation", "sparql")
                        .increment();
                if (attempt >= MAX_TRANSIENT_RETRIES) {
                    throw exception;
                }
                waitBeforeRetry(null, attempt);
            } finally {
                sample.stop(meters.timer("cabinet.provider.duration", "provider", "WIKIDATA",
                        "operation", "sparql", "outcome", outcome));
            }
        }
    }

    private int maxRetries(HttpStatusCode statusCode) {
        if (statusCode.value() == 429) {
            return MAX_RATE_LIMIT_RETRIES;
        }
        return statusCode.value() == 502 || statusCode.value() == 503 || statusCode.value() == 504
                ? MAX_TRANSIENT_RETRIES
                : 0;
    }

    private void waitBeforeRetry(RestClientResponseException exception, int attempt) {
        long fallbackMillis = Duration.ofSeconds(1L << attempt).toMillis();
        long delayMillis = exception == null
                ? fallbackMillis
                : retryAfterMillis(exception).orElse(fallbackMillis);
        delayMillis = Math.max(0, Math.min(delayMillis, MAX_RATE_LIMIT_BACKOFF.toMillis()));
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Wikidata retry was interrupted", interrupted);
        }
    }

    private List<String> awardQids(JsonNode body) {
        JsonNode bindings = body.path("results").path("bindings");
        if (!bindings.isArray() || bindings.isEmpty()) {
            return List.of();
        }
        Set<String> qids = new LinkedHashSet<>();
        for (JsonNode binding : bindings) {
            String awardQid = qid(value(binding, "award"));
            if (validQid(awardQid)) {
                qids.add(awardQid);
            }
        }
        return List.copyOf(qids);
    }

    private void logFailure(String queryType, String subject, RuntimeException exception) {
        Throwable rootCause = exception;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }
        String subjectSuffix = subject == null ? "" : " for " + subject;
        String detail = rootCause.getMessage() == null ? "no detail" : rootCause.getMessage();
        LOGGER.warn(
                "Wikidata {} query failed{} ({}: {})",
                queryType,
                subjectSuffix,
                rootCause.getClass().getSimpleName(),
                detail
        );
        LOGGER.debug("Wikidata {} query failure stack trace", queryType, exception);
    }

    private Optional<Long> retryAfterMillis(RestClientResponseException exception) {
        if (exception.getResponseHeaders() == null) {
            return Optional.empty();
        }
        String value = exception.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Duration.ofSeconds(Long.parseLong(value.trim())).toMillis());
        } catch (NumberFormatException ignored) {
            try {
                Instant retryAt = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                return Optional.of(Math.max(0, Duration.between(Instant.now(clock), retryAt).toMillis()));
            } catch (DateTimeParseException invalidDate) {
                return Optional.empty();
            }
        }
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

    private List<WikidataAward> parseAwards(JsonNode body) {
        JsonNode bindings = body.path("results").path("bindings");
        if (!bindings.isArray() || bindings.isEmpty()) {
            return List.of();
        }

        Map<String, WikidataAward> awards = new LinkedHashMap<>();
        for (JsonNode binding : bindings) {
            String statementId = value(binding, "statement");
            String categoryQid = qid(value(binding, "award"));
            String categoryName = value(binding, "awardLabel");
            if (statementId == null || !validQid(categoryQid) || categoryName == null) {
                continue;
            }
            AwardResult result;
            try {
                result = AwardResult.valueOf(value(binding, "result"));
            } catch (IllegalArgumentException | NullPointerException exception) {
                continue;
            }

            String rawDate = value(binding, "date");
            Integer precisionValue = integer(value(binding, "datePrecision"));
            AwardDatePrecision precision = precision(precisionValue);
            LocalDate eventDate = precision == AwardDatePrecision.DAY ? date(rawDate) : null;
            Integer eventYear = year(rawDate);
            WikidataAward candidate = new WikidataAward(
                    statementId,
                    result,
                    qid(value(binding, "program")),
                    value(binding, "programLabel"),
                    categoryQid,
                    categoryName,
                    qid(value(binding, "ceremony")),
                    value(binding, "ceremonyLabel"),
                    eventDate,
                    eventYear,
                    precision,
                    qid(value(binding, "work")),
                    value(binding, "workLabel"),
                    sourceUrl(statementId)
            );
            awards.merge(statementId, candidate, this::preferMoreCompleteAward);
        }
        return new ArrayList<>(awards.values());
    }

    private WikidataAward preferMoreCompleteAward(WikidataAward current, WikidataAward candidate) {
        return completeness(candidate) > completeness(current) ? candidate : current;
    }

    private int completeness(WikidataAward award) {
        int score = 0;
        if (award.programQid() != null) score++;
        if (award.ceremonyQid() != null) score++;
        if (award.eventYear() != null) score++;
        if (award.workQid() != null) score++;
        return score;
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

    private Integer integer(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String sourceUrl(String statementId) {
        String statementKey = qid(statementId);
        int separator = statementKey == null ? -1 : statementKey.indexOf('-');
        String subjectQid = separator < 0 ? null : statementKey.substring(0, separator);
        return validQid(subjectQid) ? "https://www.wikidata.org/wiki/" + subjectQid : null;
    }

    private Integer year(String value) {
        LocalDate parsed = date(value);
        return parsed == null ? null : parsed.getYear();
    }

    private AwardDatePrecision precision(Integer value) {
        if (value == null) return null;
        if (value >= 11) return AwardDatePrecision.DAY;
        if (value == 10) return AwardDatePrecision.MONTH;
        if (value == 9) return AwardDatePrecision.YEAR;
        return null;
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

    public record WikidataAwards(boolean incomplete, List<WikidataAward> items) {
    }

    public record WikidataAward(
            String statementId,
            AwardResult result,
            String programQid,
            String programName,
            String categoryQid,
            String categoryName,
            String ceremonyQid,
            String ceremonyName,
            LocalDate eventDate,
            Integer eventYear,
            AwardDatePrecision datePrecision,
            String workQid,
            String workName,
            String sourceUrl
    ) {
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
