package com.scriptles.cabinet.media.external;

import com.scriptles.cabinet.media.config.ExternalApiProperties;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.AwardDatePrecision;
import com.scriptles.cabinet.media.enums.AwardResult;
import com.scriptles.cabinet.media.enums.MediaRelationType;
import com.scriptles.cabinet.media.enums.MediaType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class WikidataClientTest {
    private static final String SPARQL_URL = "https://wikidata.test/sparql";

    private MockRestServiceServer server;
    private WikidataClient client;
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        ExternalApiProperties properties = new ExternalApiProperties(
                null,
                null,
                null,
                null,
                new ExternalApiProperties.Wikidata(SPARQL_URL, "cabinet-test/1.0")
        );
        clock = new MutableClock(Instant.parse("2026-07-14T12:00:00Z"));
        client = new WikidataClient(builder, properties, clock);
    }

    @Test
    void resolvesBookEditionAndCanonicalWorkUsingExactIdentifiers() {
        expectQuery(allOf(
                containsString("wdt:P675"),
                containsString("wdt:P212"),
                containsString("wdt:P957"),
                containsString("wdt:P629")
        ), """
                {
                  "results": {"bindings": [{
                    "item": {"value": "http://www.wikidata.org/entity/Q100"},
                    "work": {"value": "http://www.wikidata.org/entity/Q101"}
                  }]}
                }
                """);

        WikidataClient.WikidataEnrichment result = client.findBook(
                "google-volume", "978-0-123456-47-2", "0-123456-47-9", "pt-BR"
        ).orElseThrow();

        assertThat(result.wikidataId()).isEqualTo("Q100");
        assertThat(result.canonicalWorkWikidataId()).isEqualTo("Q101");
        server.verify();
    }

    @Test
    void findsBookAndSoundtrackFromMovieInOneAggregatedQuery() {
        expectQuery(allOf(
                containsString("wdt:P4947"),
                containsString("wdt:P144"),
                containsString("wdt:P406"),
                containsString("?related%20rdfs:label%20?relatedLabel"),
                containsString("LIMIT%2012")
        ), """
                {
                  "results": {"bindings": [
                    {
                      "related": {"value": "http://www.wikidata.org/entity/Q200"},
                      "relationType": {"value": "ADAPTATION_OF"},
                      "title": {"value": "The Neverending Story"},
                      "releaseDate": {"value": "1979-09-01T00:00:00Z"},
                      "bookId": {"value": "book-volume"}
                    },
                    {
                      "related": {"value": "http://www.wikidata.org/entity/Q201"},
                      "relationType": {"value": "SOUNDTRACK"},
                      "title": {"value": "Original Motion Picture Soundtrack"},
                      "releaseDate": {"value": "1984-04-06T00:00:00Z"},
                      "albumId": {"value": "11111111-1111-1111-1111-111111111111"}
                    },
                    {
                      "related": {"value": "http://www.wikidata.org/entity/Q202"},
                      "relationType": {"value": "SOUNDTRACK"},
                      "title": {"value": "Die unendliche Geschichte"},
                      "releaseDate": {"value": "1984-04-06T00:00:00Z"},
                      "albumId": {"value": "33333333-3333-3333-3333-333333333333"}
                    }
                  ]}
                }
                """);

        WikidataClient.WikidataRelations result = client.findRelations(new WikidataClient.RelationLookup(
                ExternalSource.TMDB,
                MediaType.MOVIE,
                "34584",
                null,
                null,
                null,
                "pt-BR",
                12
        ));

        assertThat(result.incomplete()).isFalse();
        assertThat(result.items()).extracting(WikidataClient.RelatedMedia::relationType)
                .containsExactly(
                        MediaRelationType.ADAPTATION_OF,
                        MediaRelationType.SOUNDTRACK,
                        MediaRelationType.SOUNDTRACK
                );
        assertThat(result.items().get(0).providerSource()).isEqualTo(ExternalSource.GOOGLE_BOOKS);
        assertThat(result.items().get(1).providerSource()).isEqualTo(ExternalSource.MUSICBRAINZ);
        server.verify();
    }

    @Test
    void followsCanonicalBookWorkToAdaptationsAndDeduplicatesQids() {
        expectQuery(allOf(
                containsString("BIND(wd:Q300"),
                containsString("wdt:P629"),
                containsString("wdt:P144"),
                containsString("LIMIT%202")
        ), """
                {
                  "results": {"bindings": [
                    {
                      "related": {"value": "http://www.wikidata.org/entity/Q301"},
                      "relationType": {"value": "ADAPTED_AS"},
                      "title": {"value": "A Film"},
                      "movieId": {"value": "123"}
                    },
                    {
                      "related": {"value": "http://www.wikidata.org/entity/Q301"},
                      "relationType": {"value": "ADAPTED_AS"},
                      "title": {"value": "A Film"},
                      "movieId": {"value": "123"}
                    }
                  ]}
                }
                """);

        WikidataClient.WikidataRelations result = client.findRelations(new WikidataClient.RelationLookup(
                ExternalSource.GOOGLE_BOOKS,
                MediaType.BOOK,
                "volume",
                "Q300",
                null,
                null,
                "pt-BR",
                2
        ));

        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.relationType()).isEqualTo(MediaRelationType.ADAPTED_AS);
            assertThat(item.type()).isEqualTo(MediaType.MOVIE);
            assertThat(item.providerExternalId()).isEqualTo("123");
        });
        server.verify();
    }

    @Test
    void findsSeriesThatUsesAnAlbum() {
        expectQuery(containsString("wdt:P406"), """
                {
                  "results": {"bindings": [{
                    "related": {"value": "http://www.wikidata.org/entity/Q401"},
                    "relationType": {"value": "SOUNDTRACK_OF"},
                    "title": {"value": "A Series"},
                    "seriesId": {"value": "456"}
                  }]}
                }
                """);

        WikidataClient.WikidataRelations found = client.findRelations(new WikidataClient.RelationLookup(
                ExternalSource.MUSICBRAINZ,
                MediaType.ALBUM,
                "22222222-2222-2222-2222-222222222222",
                null,
                null,
                null,
                "pt-BR",
                12
        ));
        assertThat(found.items()).singleElement().satisfies(item -> {
            assertThat(item.relationType()).isEqualTo(MediaRelationType.SOUNDTRACK_OF);
            assertThat(item.type()).isEqualTo(MediaType.SERIES);
            assertThat(item.providerExternalId()).isEqualTo("456");
        });

        server.verify();
    }

    @Test
    void marksProviderFailureAsIncomplete() {
        server.expect(requestTo(startsWith(SPARQL_URL))).andRespond(withServerError());

        WikidataClient.WikidataRelations failed = client.findRelations(new WikidataClient.RelationLookup(
                ExternalSource.TMDB,
                MediaType.MOVIE,
                "999",
                null,
                null,
                null,
                "pt-BR",
                12
        ));
        assertThat(failed.incomplete()).isTrue();
        assertThat(failed.items()).isEmpty();
        server.verify();
    }

    @Test
    void servesLastValidRelationsAfterCacheExpiryWhenWikidataIsUnavailable() {
        expectQuery(containsString("wdt:P406"), """
                {
                  "results": {"bindings": [{
                    "related": {"value": "http://www.wikidata.org/entity/Q501"},
                    "relationType": {"value": "SOUNDTRACK"},
                    "title": {"value": "Cached soundtrack"},
                    "albumId": {"value": "44444444-4444-4444-4444-444444444444"}
                  }]}
                }
                """);
        server.expect(requestTo(startsWith(SPARQL_URL))).andRespond(withServerError());

        WikidataClient.RelationLookup lookup = new WikidataClient.RelationLookup(
                ExternalSource.TMDB,
                MediaType.MOVIE,
                "501",
                null,
                null,
                null,
                "pt-BR",
                12
        );
        assertThat(client.findRelations(lookup).incomplete()).isFalse();

        clock.advance(Duration.ofHours(7));

        WikidataClient.WikidataRelations fallback = client.findRelations(lookup);
        assertThat(fallback.incomplete()).isTrue();
        assertThat(fallback.items()).singleElement()
                .extracting(WikidataClient.RelatedMedia::title)
                .isEqualTo("Cached soundtrack");
        server.verify();
    }

    @Test
    void preservesLastValidEnrichmentWhenRefreshFails() {
        expectQuery(containsString("wdt:P4947"), """
                {
                  "results": {"bindings": [{
                    "item": {"value": "http://www.wikidata.org/entity/Q601"}
                  }]}
                }
                """);
        server.expect(requestTo(startsWith(SPARQL_URL))).andRespond(withServerError());

        assertThat(client.find(ExternalSource.TMDB, MediaType.MOVIE, "601", "pt-BR"))
                .get()
                .extracting(WikidataClient.WikidataEnrichment::wikidataId)
                .isEqualTo("Q601");

        clock.advance(Duration.ofHours(7));

        assertThat(client.find(ExternalSource.TMDB, MediaType.MOVIE, "601", "pt-BR"))
                .get()
                .extracting(WikidataClient.WikidataEnrichment::wikidataId)
                .isEqualTo("Q601");
        server.verify();
    }

    @Test
    void findsStructuredRelevantAwardsAndPreservesDatePrecision() {
        expectQuery(allOf(
                containsString("p:P166"),
                containsString("p:P1411"),
                containsString("Q4220917"),
                containsString("Q1407225"),
                containsString("Q378427"),
                containsString("Q1364556"),
                containsString("FILTER%20EXISTS"),
                containsString("pq:P805"),
                containsString("pq:P1686"),
                containsString("wdt:P1269"),
                containsString("wikibase:language%20%22pt,en%22"),
                containsString("wikibase:timePrecision")
        ), """
                {
                  "results": {"bindings": [
                    {
                      "statement": {"value": "http://www.wikidata.org/entity/statement/Q38111-win"},
                      "result": {"value": "WIN"},
                      "award": {"value": "http://www.wikidata.org/entity/Q103916"},
                      "awardLabel": {"value": "Óscar de melhor ator"},
                      "program": {"value": "http://www.wikidata.org/entity/Q19020"},
                      "programLabel": {"value": "Óscar"},
                      "ceremony": {"value": "http://www.wikidata.org/entity/Q20022969"},
                      "ceremonyLabel": {"value": "Oscar 2016"},
                      "date": {"value": "2016-02-28T00:00:00Z"},
                      "datePrecision": {"value": "11"},
                      "work": {"value": "http://www.wikidata.org/entity/Q18002795"},
                      "workLabel": {"value": "The Revenant"}
                    },
                    {
                      "statement": {"value": "http://www.wikidata.org/entity/statement/Q38111-nomination"},
                      "result": {"value": "NOMINATION"},
                      "award": {"value": "http://www.wikidata.org/entity/Q106291"},
                      "awardLabel": {"value": "Óscar de melhor ator coadjuvante"},
                      "date": {"value": "1994-01-01T00:00:00Z"},
                      "datePrecision": {"value": "9"}
                    }
                  ]}
                }
                """);

        WikidataClient.WikidataAwards result = client.findAwards("Q38111");

        assertThat(result.incomplete()).isFalse();
        assertThat(result.items()).hasSize(2);
        assertThat(result.items().getFirst()).satisfies(award -> {
            assertThat(award.result()).isEqualTo(AwardResult.WIN);
            assertThat(award.programQid()).isEqualTo("Q19020");
            assertThat(award.ceremonyQid()).isEqualTo("Q20022969");
            assertThat(award.eventDate()).isEqualTo(LocalDate.of(2016, 2, 28));
            assertThat(award.eventYear()).isEqualTo(2016);
            assertThat(award.datePrecision()).isEqualTo(AwardDatePrecision.DAY);
            assertThat(award.workQid()).isEqualTo("Q18002795");
            assertThat(award.sourceUrl()).isEqualTo("https://www.wikidata.org/wiki/Q38111");
        });
        assertThat(result.items().get(1).eventDate()).isNull();
        assertThat(result.items().get(1).eventYear()).isEqualTo(1994);
        assertThat(result.items().get(1).datePrecision()).isEqualTo(AwardDatePrecision.YEAR);
        server.verify();
    }

    @Test
    void marksAwardProviderFailureAsIncomplete() {
        server.expect(requestTo(startsWith(SPARQL_URL))).andRespond(withServerError());

        WikidataClient.WikidataAwards result = client.findAwards("Q38111");

        assertThat(result.incomplete()).isTrue();
        assertThat(result.items()).isEmpty();
        server.verify();
    }

    @Test
    void retriesAwardQueryAfterWikidataRateLimit() {
        server.expect(requestTo(startsWith(SPARQL_URL)))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .header(HttpHeaders.RETRY_AFTER, "0"));
        expectQuery(containsString("p:P166"), """
                {"results":{"bindings":[]}}
                """);

        WikidataClient.WikidataAwards result = client.findAwards("Q38111");

        assertThat(result.incomplete()).isFalse();
        assertThat(result.items()).isEmpty();
        server.verify();
    }

    private void expectQuery(org.hamcrest.Matcher<String> queryMatcher, String body) {
        server.expect(requestTo(startsWith(SPARQL_URL)))
                .andExpect(queryParam("query", queryMatcher))
                .andExpect(queryParam("format", "json"))
                .andExpect(header("User-Agent", "cabinet-test/1.0"))
                .andRespond(withSuccess(body, org.springframework.http.MediaType.APPLICATION_JSON));
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
