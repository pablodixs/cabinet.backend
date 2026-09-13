package com.scriptles.cabinet.media.external;

import com.scriptles.cabinet.media.config.ExternalApiProperties;
import com.scriptles.cabinet.media.enums.CreditRole;
import com.scriptles.cabinet.media.enums.ExternalOfferType;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class TmdbClient implements ExternalMediaProvider, ExternalPersonWorksProvider {
    private static final String IMAGE_BASE_URL = "https://image.tmdb.org/t/p";
    private static final String POSTER_SIZE = "w500";
    private static final String BACKDROP_SIZE = "original";
    private static final String LOGO_SIZE = "original";
    private static final String STILL_SIZE = "w780";
    private static final String PROFILE_SIZE = "w500";

    private final RestClient.Builder coreRestClientBuilder;
    private final RestClient.Builder enrichmentRestClientBuilder;
    private final ExternalApiProperties properties;

    public TmdbClient(RestClient.Builder restClientBuilder, ExternalApiProperties properties) {
        this(restClientBuilder, restClientBuilder, properties);
    }

    @Autowired
    public TmdbClient(
            @Qualifier("externalCoreRestClientBuilder") RestClient.Builder coreRestClientBuilder,
            @Qualifier("externalEnrichmentRestClientBuilder") RestClient.Builder enrichmentRestClientBuilder,
            ExternalApiProperties properties
    ) {
        this.coreRestClientBuilder = coreRestClientBuilder;
        this.enrichmentRestClientBuilder = enrichmentRestClientBuilder;
        this.properties = properties;
    }

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
        return search(mediaType, query, null);
    }

    @Override
    public List<ExternalMedia> search(MediaType mediaType, String query, String language) {
        return search(mediaType, query, language, 0, 20);
    }

    @Override
    public List<ExternalMedia> search(MediaType mediaType, String query, String language, int offset, int limit) {
        List<ExternalMedia> results = new ArrayList<>();
        for (JsonNode item : searchItems(searchPath(mediaType), query, language, offset, limit)) {
            results.add(toMedia(item, mediaType, false));
        }
        return results;
    }

    @Override
    public List<ExternalMedia> searchAll(String query, String language) {
        return searchAll(query, language, 0, 20);
    }

    @Override
    public List<ExternalMedia> searchAll(String query, String language, int offset, int limit) {
        List<ExternalMedia> results = new ArrayList<>();
        for (JsonNode item : searchItems("/search/multi", query, language, offset, limit)) {
            MediaType type = mediaType(text(item, "media_type"));
            if (type != null) {
                results.add(toMedia(item, type, false));
            }
        }
        return results;
    }

    @Override
    public Optional<ExternalMedia> findById(MediaType mediaType, String externalId) {
        return findById(mediaType, externalId, null);
    }

    @Override
    public Optional<ExternalMedia> findById(MediaType mediaType, String externalId, String language) {
        return findEnrichmentById(mediaType, externalId, language);
    }

    @Override
    public Optional<ExternalMedia> findCoreById(MediaType mediaType, String externalId, String language) {
        JsonNode body = get(detailPath(mediaType, externalId), null, language, false, null);
        return body.isMissingNode() || body.isEmpty()
                ? Optional.empty()
                : Optional.of(toMedia(body, mediaType, true));
    }

    @Override
    public Optional<ExternalMedia> findEnrichmentById(MediaType mediaType, String externalId, String language) {
        JsonNode body = get(detailPath(mediaType, externalId), null, language, true, null);
        return body.isMissingNode() || body.isEmpty() ? Optional.empty() : Optional.of(toMedia(body, mediaType, true));
    }

    public Optional<String> findPersonWikidataId(String personId) {
        JsonNode body = get("/person/" + personId + "/external_ids", null, null, false, null);
        return Optional.ofNullable(text(body, "wikidata_id"));
    }

    @Override
    public PersonWorks findPersonWorks(String personExternalId, String language) {
        JsonNode body = get("/person/" + personExternalId + "/movie_credits", null, language, false, null);
        List<Work> works = new ArrayList<>();
        addPersonWorks(works, body.path("cast"), CreditRole.ACTOR, true);
        addPersonWorks(works, body.path("crew"), null, false);
        works.sort(java.util.Comparator
                .comparingDouble(Work::relevance).reversed()
                .thenComparing(
                        work -> work.media().releaseDate(),
                        java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder()))
                .thenComparing(work -> work.media().externalId()));
        java.util.LinkedHashMap<String, Work> distinct = new java.util.LinkedHashMap<>();
        works.forEach(work -> distinct.putIfAbsent(work.media().externalId(), work));
        return new PersonWorks(List.copyOf(distinct.values()), false);
    }

    private void addPersonWorks(
            List<Work> works,
            JsonNode credits,
            CreditRole fixedRole,
            boolean cast
    ) {
        for (JsonNode credit : credits) {
            CreditRole role = cast ? fixedRole : personWorkRole(text(credit, "job"));
            if (role == null
                    || credit.path("adult").asBoolean(false)
                    || text(credit, "id") == null) {
                continue;
            }
            works.add(new Work(
                    toMedia(credit, MediaType.MOVIE, false),
                    role,
                    cast ? text(credit, "character") : null,
                    credit.path("popularity").asDouble(0)
            ));
        }
    }

    private CreditRole personWorkRole(String job) {
        if (job == null) {
            return null;
        }
        return switch (job.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "director" -> CreditRole.DIRECTOR;
            default -> null;
        };
    }

    public Optional<String> findImdbId(MediaType mediaType, String externalId) {
        if (!supports(mediaType)) {
            return Optional.empty();
        }
        JsonNode body = get(detailPath(mediaType, externalId) + "/external_ids", null, null, false, null);
        return Optional.ofNullable(text(body, "imdb_id"));
    }

    public ExternalAvailability findWatchProviders(MediaType mediaType, String externalId, String countryCode) {
        if (!supports(mediaType)) {
            throw new IllegalArgumentException("TMDB watch providers only support movies and series");
        }

        JsonNode body = get(detailPath(mediaType, externalId) + "/watch/providers", null, null, false, null);
        JsonNode country = body.path("results").path(countryCode);
        String sourceUrl = text(country, "link");
        List<ExternalAvailability.Offer> offers = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        addWatchOffers(offers, seen, country.path("flatrate"), ExternalOfferType.SUBSCRIPTION, sourceUrl);
        addWatchOffers(offers, seen, country.path("free"), ExternalOfferType.FREE, sourceUrl);
        addWatchOffers(offers, seen, country.path("ads"), ExternalOfferType.ADS, sourceUrl);
        addWatchOffers(offers, seen, country.path("rent"), ExternalOfferType.RENT, sourceUrl);
        addWatchOffers(offers, seen, country.path("buy"), ExternalOfferType.BUY, sourceUrl);
        return new ExternalAvailability(ExternalSource.JUSTWATCH, "JustWatch", sourceUrl, offers);
    }

    private void addWatchOffers(
            List<ExternalAvailability.Offer> result,
            Set<String> seen,
            JsonNode providers,
            ExternalOfferType type,
            String sourceUrl
    ) {
        for (JsonNode provider : providers) {
            String providerId = text(provider, "provider_id");
            String name = text(provider, "provider_name");
            String key = providerId + ':' + type;
            if (name == null || !seen.add(key)) {
                continue;
            }
            result.add(new ExternalAvailability.Offer(
                    providerId,
                    name,
                    imageUrl(text(provider, "logo_path"), "w92"),
                    type,
                    sourceUrl,
                    integer(provider, "display_priority")
            ));
        }
    }

    private JsonNode get(String path, String query, String language, boolean includeCredits, Integer page) {
        boolean hasAccessToken = hasText(properties.tmdb().accessToken());
        if (!hasAccessToken && !hasText(properties.tmdb().apiKey())) {
            throw new ExternalMediaException("Configure TMDB_ACCESS_TOKEN or TMDB_API_KEY");
        }

        try {
            RestClient.Builder selectedBuilder = includeCredits
                    ? enrichmentRestClientBuilder
                    : coreRestClientBuilder;
            RestClient.RequestHeadersSpec<?> request = selectedBuilder.clone()
                    .baseUrl(properties.tmdb().baseUrl()).build().get()
                    .uri(uriBuilder -> {
                        uriBuilder.path(path);
                        if (!hasAccessToken) {
                            uriBuilder.queryParam("api_key", properties.tmdb().apiKey());
                        }
                        if (query != null) {
                            uriBuilder.queryParam("query", query);
                            uriBuilder.queryParam("include_adult", false);
                            uriBuilder.queryParam("page", page == null ? 1 : page);
                        }
                        if (language != null) {
                            uriBuilder.queryParam("language", language);
                        }
                        if (includeCredits) {
                            uriBuilder.queryParam("append_to_response", "credits,images");
                            uriBuilder.queryParam("include_image_language", imageLanguages(language));
                        }
                        return uriBuilder.build();
                    });
            if (hasAccessToken) {
                request.header("Authorization", "Bearer " + properties.tmdb().accessToken());
            }
            return request
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 429) {
                throw new ExternalMediaRateLimitException("TMDB rate limit exceeded", exception);
            }
            throw new ExternalMediaException("TMDB responded with HTTP " + exception.getStatusCode().value(), exception);
        } catch (RestClientException exception) {
            throw new ExternalMediaException("Unable to query TMDB", exception);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private List<JsonNode> searchItems(String path, String query, String language, int offset, int limit) {
        int firstPage = offset / 20 + 1;
        int lastPage = (offset + limit - 1) / 20 + 1;
        List<JsonNode> items = new ArrayList<>();
        for (int page = firstPage; page <= lastPage; page++) {
            JsonNode body = get(path, query, language, false, page);
            body.path("results").forEach(items::add);
        }
        int fromIndex = Math.min(offset % 20, items.size());
        int toIndex = Math.min(fromIndex + limit, items.size());
        return items.subList(fromIndex, toIndex);
    }

    private MediaType mediaType(String value) {
        return switch (value) {
            case "movie" -> MediaType.MOVIE;
            case "tv" -> MediaType.SERIES;
            default -> null;
        };
    }

    private ExternalMedia toMedia(JsonNode node, MediaType type, boolean detailed) {
        boolean movie = type == MediaType.MOVIE;
        String title = text(node, movie ? "title" : "name");
        String originalTitle = text(node, movie ? "original_title" : "original_name");
        LocalDate releaseDate = date(text(node, movie ? "release_date" : "first_air_date"));
        String id = text(node, "id");
        List<ExternalMedia.ExternalCredit> credits = credits(node, type, detailed);

        return new ExternalMedia(
                ExternalSource.TMDB, id, type, title, originalTitle, text(node, "overview"),
                detailed ? text(node, "tagline") : null,
                imageUrl(text(node, "poster_path"), POSTER_SIZE),
                "https://www.themoviedb.org/%s/%s".formatted(movie ? "movie" : "tv", id),
                null, releaseDate, text(node, "original_language"), countryCode(node), null, null, null, null,
                null, null,
                detailed && movie ? integer(node, "runtime") : null,
                detailed && movie ? longValue(node, "budget") : null,
                detailed && movie ? longValue(node, "revenue") : null,
                detailed && !movie ? text(node, "status") : null,
                detailed && !movie ? integer(node, "number_of_seasons") : null,
                detailed && !movie ? integer(node, "number_of_episodes") : null,
                detailed && !movie ? date(text(node, "last_air_date")) : null,
                null, null, creator(node, type, detailed),
                imageUrl(text(node, "backdrop_path"), BACKDROP_SIZE), detailed ? logoUrl(node) : null,
                detailed ? genres(node) : List.of(), List.of(), detailed && !movie ? seasons(node) : List.of(),
                credits
        );
    }

    public List<ExternalMedia.ExternalEpisode> findSeasonEpisodes(String seriesId, int seasonNumber, String language) {
        JsonNode body = get("/tv/%s/season/%d".formatted(seriesId, seasonNumber), null, language, false, null);
        List<ExternalMedia.ExternalEpisode> episodes = new ArrayList<>();
        for (JsonNode episode : body.path("episodes")) {
            episodes.add(new ExternalMedia.ExternalEpisode(
                    text(episode, "id"), integer(episode, "episode_number"), text(episode, "name"),
                    text(episode, "overview"), imageUrl(text(episode, "still_path"), STILL_SIZE),
                    date(text(episode, "air_date")), integer(episode, "runtime")
            ));
        }
        return episodes;
    }

    public SeriesTrackingSnapshot findSeriesTrackingSnapshot(String seriesId, String language) {
        JsonNode body = get(detailPath(MediaType.SERIES, seriesId), null, language, true, null);
        if (body.isMissingNode() || body.isEmpty()) {
            throw new ExternalMediaException("TV series was not found on TMDB");
        }
        return new SeriesTrackingSnapshot(
                toMedia(body, MediaType.SERIES, true),
                integer(body.path("last_episode_to_air"), "season_number"),
                integer(body.path("next_episode_to_air"), "season_number")
        );
    }

    public record SeriesTrackingSnapshot(
            ExternalMedia media,
            Integer lastEpisodeSeasonNumber,
            Integer nextEpisodeSeasonNumber
    ) {
    }

    private List<ExternalMedia.ExternalGenre> genres(JsonNode node) {
        List<ExternalMedia.ExternalGenre> result = new ArrayList<>();
        for (JsonNode genre : node.path("genres")) {
            result.add(new ExternalMedia.ExternalGenre(text(genre, "id"), text(genre, "name"), ExternalSource.TMDB));
        }
        return result;
    }

    private List<ExternalMedia.ExternalSeason> seasons(JsonNode node) {
        List<ExternalMedia.ExternalSeason> result = new ArrayList<>();
        for (JsonNode season : node.path("seasons")) {
            result.add(new ExternalMedia.ExternalSeason(
                    text(season, "id"), integer(season, "season_number"), text(season, "name"),
                    text(season, "overview"), imageUrl(text(season, "poster_path"), POSTER_SIZE),
                    integer(season, "episode_count"), date(text(season, "air_date"))
            ));
        }
        return result;
    }

    private String logoUrl(JsonNode node) {
        JsonNode logos = node.path("images").path("logos");
        return logos.isArray() && !logos.isEmpty()
                ? imageUrl(text(logos.get(0), "file_path"), LOGO_SIZE)
                : null;
    }

    private String imageLanguages(String language) {
        String code = language == null ? null : language.substring(0, 2);
        return code == null ? "en,null" : code + ",en,null";
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

    private String imageUrl(String path, String size) {
        return path == null ? null : IMAGE_BASE_URL + "/" + size + path;
    }

    private String creator(JsonNode node, MediaType type, boolean detailed) {
        if (!detailed) {
            return null;
        }
        if (type == MediaType.SERIES) {
            return names(node.path("created_by"), "name");
        }
        List<String> directors = new ArrayList<>();
        for (JsonNode crewMember : node.path("credits").path("crew")) {
            if ("Director".equals(text(crewMember, "job"))) {
                String name = text(crewMember, "name");
                if (name != null) {
                    directors.add(name);
                }
            }
        }
        return directors.isEmpty() ? null : String.join(", ", directors);
    }

    private List<ExternalMedia.ExternalCredit> credits(JsonNode node, MediaType type, boolean detailed) {
        if (!detailed) {
            return List.of();
        }

        List<ExternalMedia.ExternalCredit> result = new ArrayList<>();
        if (type == MediaType.SERIES) {
            int position = 0;
            for (JsonNode creator : node.path("created_by")) {
                addCredit(result, creator, CreditRole.CREATOR, null, position++);
            }
        }

        int crewPosition = 0;
        for (JsonNode crewMember : node.path("credits").path("crew")) {
            CreditRole role = crewRole(text(crewMember, "job"));
            if (role != null) {
                addCredit(result, crewMember, role, null, crewPosition++);
            }
        }

        for (JsonNode castMember : node.path("credits").path("cast")) {
            addCredit(result, castMember, CreditRole.ACTOR, text(castMember, "character"),
                    integer(castMember, "order"));
        }
        return List.copyOf(result);
    }

    private CreditRole crewRole(String job) {
        if (job == null) {
            return null;
        }
        return switch (job) {
            case "Director" -> CreditRole.DIRECTOR;
            case "Producer", "Executive Producer" -> CreditRole.PRODUCER;
            case "Screenplay", "Writer", "Story", "Teleplay" -> CreditRole.SCREENWRITER;
            case "Original Music Composer", "Composer", "Music" -> CreditRole.COMPOSER;
            default -> null;
        };
    }

    private void addCredit(
            List<ExternalMedia.ExternalCredit> credits,
            JsonNode person,
            CreditRole role,
            String characterName,
            Integer position
    ) {
        String name = text(person, "name");
        if (name == null) {
            return;
        }
        credits.add(new ExternalMedia.ExternalCredit(
                text(person, "id"),
                name,
                role,
                characterName,
                position,
                imageUrl(text(person, "profile_path"), PROFILE_SIZE),
                ExternalSource.TMDB
        ));
    }

    private String names(JsonNode values, String field) {
        List<String> names = new ArrayList<>();
        for (JsonNode value : values) {
            String name = text(value, field);
            if (name != null) {
                names.add(name);
            }
        }
        return names.isEmpty() ? null : String.join(", ", names);
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
