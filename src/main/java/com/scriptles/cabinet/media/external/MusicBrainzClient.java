package com.scriptles.cabinet.media.external;

import com.scriptles.cabinet.media.config.ExternalApiProperties;
import com.scriptles.cabinet.media.enums.CreditRole;
import com.scriptles.cabinet.media.enums.ExternalOfferType;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class MusicBrainzClient implements ExternalMediaProvider, ExternalPersonWorksProvider {
    private static final Pattern WIKIDATA_QID = Pattern.compile("(?:^|/)(Q[1-9]\\d*)(?=$|[/?#])", Pattern.CASE_INSENSITIVE);

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
        return mediaType == MediaType.ALBUM || mediaType == MediaType.TRACK;
    }

    @Override
    public List<ExternalMedia> search(MediaType mediaType, String query) {
        return search(mediaType, query, null, 0, 20);
    }

    @Override
    public List<ExternalMedia> search(MediaType mediaType, String query, String language, int offset, int limit) {
        boolean track = mediaType == MediaType.TRACK;
        JsonNode body = get(track ? "/recording" : "/release-group", query, offset, limit);
        List<ExternalMedia> results = new ArrayList<>();
        for (JsonNode item : body.path(track ? "recordings" : "release-groups")) {
            // Cover lookup can require two additional HTTP calls per album. Keep search bounded
            // to the provider request and resolve the cover only on the details/import path.
            results.add(track ? toTrack(item) : toAlbum(item, List.of(), false));
        }
        return results;
    }

    @Override
    public Optional<ExternalMedia> findById(MediaType mediaType, String externalId) {
        if (mediaType == MediaType.TRACK) {
            JsonNode body = get("/recording/" + externalId, null, 0, 0,
                    "artist-credits+genres+releases+url-rels");
            return body.isMissingNode() || body.isEmpty() ? Optional.empty() : Optional.of(toTrack(body));
        }

        JsonNode body = get("/release-group/" + externalId, null, 0, 0,
                "artist-credits+genres+releases+media+url-rels");
        if (body.isMissingNode() || body.isEmpty()) {
            return Optional.empty();
        }
        List<ExternalMedia.ExternalTrack> tracks = findTracks(body);
        return Optional.of(toAlbum(body, tracks, true));
    }

    public Optional<String> findArtistWikidataId(String artistId) {
        JsonNode body = get("/artist/" + artistId, null, 0, 0, "url-rels");
        return Optional.ofNullable(wikidataId(body));
    }

    @Override
    public PersonWorks findPersonWorks(String personExternalId, String language) {
        JsonNode body = browseReleaseGroups(personExternalId);
        List<Work> works = new ArrayList<>();
        for (JsonNode item : body.path("release-groups")) {
            ExternalMedia album = toAlbum(item, List.of(), false);
            if (album.externalId() != null) {
                works.add(new Work(album, CreditRole.ARTIST, null, 0));
            }
        }
        works.sort(java.util.Comparator
                .comparing(
                        (Work work) -> work.media().releaseDate(),
                        java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder()))
                .thenComparing(work -> work.media().title(), java.util.Comparator.nullsLast(String::compareToIgnoreCase))
                .thenComparing(work -> work.media().externalId()));
        Integer total = integer(body, "release-group-count");
        return new PersonWorks(List.copyOf(works), total != null && total > works.size());
    }

    private JsonNode browseReleaseGroups(String artistId) {
        waitForRateLimit();
        try {
            return restClientBuilder.clone().baseUrl(properties.musicbrainz().baseUrl()).build().get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/release-group")
                            .queryParam("fmt", "json")
                            .queryParam("artist", artistId)
                            .queryParam("inc", "artist-credits")
                            .queryParam("release-group-status", "website-default")
                            .queryParam("offset", 0)
                            .queryParam("limit", 100)
                            .build())
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

    public ExternalAvailability findListenAndBuyLinks(MediaType mediaType, String externalId) {
        if (!supports(mediaType)) {
            throw new IllegalArgumentException("MusicBrainz links only support albums and tracks");
        }
        if (mediaType == MediaType.TRACK) {
            JsonNode recording = get("/recording/" + externalId, null, 0, 0, "releases+url-rels");
            String sourceUrl = "https://musicbrainz.org/recording/" + externalId;
            List<JsonNode> relationSets = new ArrayList<>();
            relationSets.add(recording.path("relations"));
            addReleaseRelations(recording.path("releases"), relationSets);
            return availabilityFromRelations(sourceUrl, relationSets);
        }

        JsonNode releaseGroup = get("/release-group/" + externalId, null, 0, 0,
                "releases+media+url-rels");
        String sourceUrl = "https://musicbrainz.org/release-group/" + externalId;
        List<JsonNode> relationSets = new ArrayList<>();
        relationSets.add(releaseGroup.path("relations"));
        addReleaseRelations(releaseGroup.path("releases"), relationSets);
        return availabilityFromRelations(sourceUrl, relationSets);
    }

    private void addReleaseRelations(JsonNode releases, List<JsonNode> relationSets) {
        JsonNode releaseSummary = selectRelease(releases);
        String releaseId = releaseSummary == null ? null : text(releaseSummary, "id");
        if (releaseId == null) {
            return;
        }
        JsonNode release = get("/release/" + releaseId, null, 0, 0, "url-rels");
        relationSets.add(release.path("relations"));
    }

    private ExternalAvailability availabilityFromRelations(String sourceUrl, List<JsonNode> relationSets) {
        Map<String, ExternalAvailability.Offer> offers = new LinkedHashMap<>();
        for (JsonNode relations : relationSets) {
            for (JsonNode relation : relations) {
                ExternalOfferType offerType = offerType(text(relation, "type"));
                String url = text(relation.path("url"), "resource");
                if (offerType == null || url == null) {
                    continue;
                }
                String providerId = providerId(url);
                String key = offerType + ":" + url;
                offers.putIfAbsent(key, new ExternalAvailability.Offer(
                        providerId,
                        providerName(providerId),
                        null,
                        offerType,
                        url,
                        offers.size()
                ));
            }
        }
        return new ExternalAvailability(
                ExternalSource.MUSICBRAINZ,
                "MusicBrainz",
                sourceUrl,
                List.copyOf(offers.values())
        );
    }

    private ExternalOfferType offerType(String relationType) {
        if (relationType == null) {
            return null;
        }
        return switch (relationType.toLowerCase(Locale.ROOT)) {
            case "streaming", "free streaming", "stream for free", "streaming page", "stream video for free" ->
                    ExternalOfferType.STREAM;
            case "purchase for download", "purchase music for download" -> ExternalOfferType.BUY_DOWNLOAD;
            case "purchase for mail-order", "purchase for mail order" -> ExternalOfferType.BUY_PHYSICAL;
            case "download for free", "free download" -> ExternalOfferType.FREE_DOWNLOAD;
            default -> null;
        };
    }

    private String providerId(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null ? url : host.toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
        } catch (IllegalArgumentException exception) {
            return url;
        }
    }

    private String providerName(String providerId) {
        String normalized = providerId.toLowerCase(Locale.ROOT);
        if (normalized.contains("spotify")) return "Spotify";
        if (normalized.contains("music.apple") || normalized.contains("itunes.apple")) return "Apple Music";
        if (normalized.contains("deezer")) return "Deezer";
        if (normalized.contains("bandcamp")) return "Bandcamp";
        if (normalized.contains("tidal")) return "TIDAL";
        if (normalized.contains("soundcloud")) return "SoundCloud";
        if (normalized.contains("youtube")) return "YouTube Music";
        if (normalized.contains("amazon")) return "Amazon Music";

        String name = normalized.split("\\.")[0].replace('-', ' ');
        return name.isBlank() ? providerId : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private JsonNode get(String path, String query, int offset, int limit) {
        return get(path, query, offset, limit, null);
    }

    private JsonNode get(String path, String query, int offset, int limit, String inc) {
        waitForRateLimit();
        try {
            return restClientBuilder.clone().baseUrl(properties.musicbrainz().baseUrl()).build().get()
                    .uri(uriBuilder -> {
                        uriBuilder.path(path).queryParam("fmt", "json");
                        if (query != null) {
                            uriBuilder.queryParam("query", query)
                                    .queryParam("offset", offset)
                                    .queryParam("limit", limit);
                        }
                        if (inc != null) {
                            uriBuilder.queryParam("inc", inc);
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

    private ExternalMedia toAlbum(
            JsonNode node,
            List<ExternalMedia.ExternalTrack> tracks,
            boolean includeCover
    ) {
        String id = text(node, "id");
        List<ExternalMedia.ExternalCredit> credits = artistCredits(node.path("artist-credit"));
        return new ExternalMedia(
                ExternalSource.MUSICBRAINZ, id, MediaType.ALBUM, text(node, "title"), null,
                text(node, "disambiguation"), null, includeCover ? albumCoverService.findCoverUrl(id) : null,
                "https://musicbrainz.org/release-group/" + id, wikidataId(node),
                date(text(node, "first-release-date")), null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                albumType(node), tracks.isEmpty() ? null : tracks.size(), creditNames(credits),
                null, null, genres(node), tracks, List.of(), credits
        );
    }

    private ExternalMedia toTrack(JsonNode node) {
        String id = text(node, "id");
        Integer length = integer(node, "length");
        List<ExternalMedia.ExternalCredit> credits = artistCredits(node.path("artist-credit"));
        return new ExternalMedia(
                ExternalSource.MUSICBRAINZ, id, MediaType.TRACK, text(node, "title"), null,
                text(node, "disambiguation"), null, null,
                "https://musicbrainz.org/recording/" + id, wikidataId(node),
                recordingReleaseDate(node), null, null, null, null, null, null,
                millisecondsToSeconds(length), null, null, null, null, null, null, null, null,
                null, null, creditNames(credits), null, null,
                genres(node), List.of(), List.of(), credits
        );
    }

    private List<ExternalMedia.ExternalTrack> findTracks(JsonNode releaseGroup) {
        JsonNode releaseSummary = selectRelease(releaseGroup.path("releases"));
        if (releaseSummary == null) {
            return List.of();
        }
        String releaseId = text(releaseSummary, "id");
        if (releaseId == null) {
            return List.of();
        }
        JsonNode release = get("/release/" + releaseId, null, 0, 0, "recordings");
        List<ExternalMedia.ExternalTrack> tracks = new ArrayList<>();
        int mediumIndex = 0;
        for (JsonNode medium : release.path("media")) {
            mediumIndex++;
            Integer discNumber = integer(medium, "position");
            if (discNumber == null) {
                discNumber = mediumIndex;
            }
            for (JsonNode track : medium.path("tracks")) {
                JsonNode recording = track.path("recording");
                Integer length = integer(track, "length");
                if (length == null) {
                    length = integer(recording, "length");
                }
                String title = text(track, "title");
                if (title == null) {
                    title = text(recording, "title");
                }
                tracks.add(new ExternalMedia.ExternalTrack(
                        text(recording, "id"), title, discNumber,
                        integer(track, "position"), millisecondsToSeconds(length), null
                ));
            }
        }
        return tracks;
    }

    private JsonNode selectRelease(JsonNode releases) {
        if (!releases.isArray() || releases.isEmpty()) {
            return null;
        }
        JsonNode best = null;
        for (JsonNode release : releases) {
            if (text(release, "id") != null && (best == null || isBetterRelease(release, best))) {
                best = release;
            }
        }
        return best;
    }

    private boolean isBetterRelease(JsonNode candidate, JsonNode current) {
        boolean candidateOfficial = "Official".equalsIgnoreCase(text(candidate, "status"));
        boolean currentOfficial = "Official".equalsIgnoreCase(text(current, "status"));
        if (candidateOfficial != currentOfficial) {
            return candidateOfficial;
        }

        int candidateTracks = releaseTrackCount(candidate);
        int currentTracks = releaseTrackCount(current);
        if (candidateTracks != currentTracks) {
            return candidateTracks > currentTracks;
        }

        String candidateDate = text(candidate, "date");
        String currentDate = text(current, "date");
        if (candidateDate == null || currentDate == null) {
            return candidateDate != null;
        }
        return candidateDate.compareTo(currentDate) < 0;
    }

    private int releaseTrackCount(JsonNode release) {
        Integer directCount = integer(release, "track-count");
        if (directCount != null) {
            return directCount;
        }
        int total = 0;
        boolean found = false;
        for (JsonNode medium : release.path("media")) {
            Integer mediumCount = integer(medium, "track-count");
            if (mediumCount != null) {
                total += mediumCount;
                found = true;
            }
        }
        return found ? total : -1;
    }

    private LocalDate recordingReleaseDate(JsonNode node) {
        LocalDate firstReleaseDate = date(text(node, "first-release-date"));
        if (firstReleaseDate != null) {
            return firstReleaseDate;
        }
        LocalDate earliest = null;
        for (JsonNode release : node.path("releases")) {
            LocalDate releaseDate = date(text(release, "date"));
            if (releaseDate != null && (earliest == null || releaseDate.isBefore(earliest))) {
                earliest = releaseDate;
            }
        }
        return earliest;
    }

    private String wikidataId(JsonNode node) {
        for (JsonNode relation : node.path("relations")) {
            if (!"wikidata".equalsIgnoreCase(text(relation, "type"))) {
                continue;
            }
            String target = text(relation.path("url"), "resource");
            if (target == null) {
                target = text(relation, "target");
            }
            if (target != null) {
                Matcher matcher = WIKIDATA_QID.matcher(target);
                if (matcher.find()) {
                    return matcher.group(1).toUpperCase();
                }
            }
        }
        return null;
    }

    private String albumType(JsonNode node) {
        for (JsonNode secondaryType : node.path("secondary-types")) {
            if ("soundtrack".equalsIgnoreCase(secondaryType.asText())) {
                return "Soundtrack";
            }
        }
        return text(node, "primary-type");
    }

    private Integer millisecondsToSeconds(Integer length) {
        return length == null ? null : length / 1_000;
    }

    private List<ExternalMedia.ExternalGenre> genres(JsonNode node) {
        List<ExternalMedia.ExternalGenre> genres = new ArrayList<>();
        for (JsonNode genre : node.path("genres")) {
            genres.add(new ExternalMedia.ExternalGenre(text(genre, "id"), text(genre, "name"), ExternalSource.MUSICBRAINZ));
        }
        return genres;
    }

    private String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private Integer integer(JsonNode node, String field) {
        return node.path(field).isNumber() ? node.path(field).asInt() : null;
    }

    private List<ExternalMedia.ExternalCredit> artistCredits(JsonNode values) {
        List<ExternalMedia.ExternalCredit> credits = new ArrayList<>();
        int position = 0;
        for (JsonNode credit : values) {
            String name = text(credit, "name");
            if (name == null) {
                name = text(credit.path("artist"), "name");
            }
            if (name != null) {
                credits.add(new ExternalMedia.ExternalCredit(
                        text(credit.path("artist"), "id"),
                        name,
                        CreditRole.ARTIST,
                        null,
                        position++,
                        null,
                        ExternalSource.MUSICBRAINZ
                ));
            }
        }
        return List.copyOf(credits);
    }

    private String creditNames(List<ExternalMedia.ExternalCredit> credits) {
        return credits.isEmpty()
                ? null
                : credits.stream().map(ExternalMedia.ExternalCredit::name)
                .collect(java.util.stream.Collectors.joining(", "));
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
