package com.scriptles.cabinet.media.external;

import com.scriptles.cabinet.media.config.ExternalApiProperties;
import com.scriptles.cabinet.media.enums.CreditRole;
import com.scriptles.cabinet.media.enums.ExternalOfferType;
import com.scriptles.cabinet.media.enums.MediaType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TmdbClientTest {
    private static final String BASE_URL = "https://tmdb.test/3";

    private MockRestServiceServer server;
    private TmdbClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
        ExternalApiProperties properties = new ExternalApiProperties(
                new ExternalApiProperties.Tmdb(BASE_URL, "api-key", ""),
                null,
                null,
                null,
                null
        );
        client = new TmdbClient(restClientBuilder, properties);
    }

    @Test
    void mapsMovieCrewAndCastToStructuredCredits() {
        server.expect(requestTo(startsWith(BASE_URL + "/movie/550")))
                .andExpect(queryParam("api_key", "api-key"))
                .andExpect(queryParam("append_to_response", "credits,images"))
                .andRespond(withSuccess("""
                        {
                          "id": 550,
                          "title": "Fight Club",
                          "credits": {
                            "crew": [
                              {"id": 7467, "name": "David Fincher", "job": "Director", "profile_path": "/fincher.jpg"},
                              {"id": 7468, "name": "Ross Grayson Bell", "job": "Producer"},
                              {"id": 7469, "name": "Jim Uhls", "job": "Screenplay"}
                            ],
                            "cast": [
                              {"id": 287, "name": "Brad Pitt", "character": "Tyler Durden", "order": 0,
                               "profile_path": "/pitt.jpg"}
                            ]
                          },
                          "images": {"logos": []}
                        }
                        """, org.springframework.http.MediaType.APPLICATION_JSON));

        ExternalMedia movie = client.findById(MediaType.MOVIE, "550", "pt-BR").orElseThrow();

        assertThat(movie.creator()).isEqualTo("David Fincher");
        assertThat(movie.credits()).extracting(ExternalMedia.ExternalCredit::role)
                .containsExactly(CreditRole.DIRECTOR, CreditRole.PRODUCER, CreditRole.SCREENWRITER, CreditRole.ACTOR);
        assertThat(movie.credits()).filteredOn(credit -> credit.role() == CreditRole.ACTOR)
                .singleElement().satisfies(actor -> {
                    assertThat(actor.externalId()).isEqualTo("287");
                    assertThat(actor.characterName()).isEqualTo("Tyler Durden");
                    assertThat(actor.position()).isZero();
                    assertThat(actor.imageUrl()).endsWith("/w500/pitt.jpg");
                });
        server.verify();
    }

    @Test
    void mapsSeriesCreatorsToTheCreatorRole() {
        server.expect(requestTo(startsWith(BASE_URL + "/tv/1396")))
                .andRespond(withSuccess("""
                        {
                          "id": 1396,
                          "name": "Breaking Bad",
                          "created_by": [
                            {"id": 66633, "name": "Vince Gilligan", "profile_path": "/gilligan.jpg"}
                          ],
                          "credits": {"crew": [], "cast": []},
                          "images": {"logos": []},
                          "seasons": []
                        }
                        """, org.springframework.http.MediaType.APPLICATION_JSON));

        ExternalMedia series = client.findById(MediaType.SERIES, "1396", "pt-BR").orElseThrow();

        assertThat(series.creator()).isEqualTo("Vince Gilligan");
        assertThat(series.credits()).singleElement().satisfies(credit -> {
            assertThat(credit.externalId()).isEqualTo("66633");
            assertThat(credit.role()).isEqualTo(CreditRole.CREATOR);
        });
        server.verify();
    }

    @Test
    void mapsTheLastAndNextEpisodeSeasonsForTracking() {
        server.expect(requestTo(startsWith(BASE_URL + "/tv/1396")))
                .andRespond(withSuccess("""
                        {
                          "id": 1396,
                          "name": "Breaking Bad",
                          "credits": {"crew": [], "cast": []},
                          "images": {"logos": []},
                          "seasons": [],
                          "last_episode_to_air": {"season_number": 4},
                          "next_episode_to_air": {"season_number": 5}
                        }
                        """, org.springframework.http.MediaType.APPLICATION_JSON));

        var snapshot = client.findSeriesTrackingSnapshot("1396", "pt-BR");

        assertThat(snapshot.media().title()).isEqualTo("Breaking Bad");
        assertThat(snapshot.lastEpisodeSeasonNumber()).isEqualTo(4);
        assertThat(snapshot.nextEpisodeSeasonNumber()).isEqualTo(5);
        server.verify();
    }

    @Test
    void resolvesPersonWikidataIdentityFromExternalIds() {
        server.expect(requestTo(startsWith(BASE_URL + "/person/7467/external_ids")))
                .andExpect(queryParam("api_key", "api-key"))
                .andRespond(withSuccess("""
                        {"id": 7467, "wikidata_id": "Q51552"}
                        """, org.springframework.http.MediaType.APPLICATION_JSON));

        assertThat(client.findPersonWikidataId("7467")).contains("Q51552");
        server.verify();
    }

    @Test
    void returnsDistinctNonAdultDirectorMoviesByPopularity() {
        server.expect(requestTo(startsWith(BASE_URL + "/person/7467/combined_credits")))
                .andExpect(queryParam("api_key", "api-key"))
                .andExpect(queryParam("language", "pt-BR"))
                .andRespond(withSuccess("""
                        {
                          "crew": [
                            {"id": 10, "media_type": "movie", "title": "Older popular", "job": "Director",
                             "release_date": "2000-01-01", "popularity": 20},
                            {"id": 11, "media_type": "movie", "title": "Newer less popular", "job": "Director",
                             "release_date": "2020-01-01", "popularity": 10},
                            {"id": 10, "media_type": "movie", "title": "Duplicate", "job": "Director", "popularity": 1},
                            {"id": 12, "media_type": "movie", "title": "Adult", "job": "Director", "adult": true, "popularity": 100},
                            {"id": 13, "media_type": "movie", "title": "Produced", "job": "Producer", "popularity": 50}
                          ]
                        }
                        """, org.springframework.http.MediaType.APPLICATION_JSON));

        ExternalPersonWorksProvider.PersonWorks works = client.findPersonWorks("7467", "pt-BR");

        assertThat(works.incomplete()).isFalse();
        assertThat(works.items()).extracting(work -> work.media().externalId())
                .containsExactly("13", "10", "11");
        assertThat(works.items()).extracting(ExternalPersonWorksProvider.Work::relevance)
                .containsExactly(50.0, 20.0, 10.0);
        assertThat(works.items().getFirst().role()).isEqualTo(CreditRole.PRODUCER);
        server.verify();
    }

    @Test
    void returnsCastAsActorAndPreservesCharacterName() {
        server.expect(requestTo(startsWith(BASE_URL + "/person/287/combined_credits")))
                .andExpect(queryParam("api_key", "api-key"))
                .andExpect(queryParam("language", "pt-BR"))
                .andRespond(withSuccess("""
                        {
                          "cast": [
                            {"id": 550, "media_type": "movie", "title": "Fight Club", "character": "Tyler Durden",
                             "release_date": "1999-01-01", "popularity": 20},
                            {"id": 551, "media_type": "movie", "title": "Seven", "adult": true, "popularity": 100},
                            {"id": 562, "media_type": "tv", "name": "The Series", "character": "Himself",
                             "first_air_date": "2020-01-01", "popularity": 12}
                          ],
                          "crew": [
                            {"id": 807, "media_type": "movie", "title": "Se7en", "job": "Director", "popularity": 10},
                            {"id": 550, "media_type": "movie", "title": "Fight Club", "job": "Producer", "popularity": 1},
                            {"id": 563, "media_type": "tv", "name": "Another Series", "job": "Executive Producer", "popularity": 5},
                            {"id": 564, "media_type": "movie", "title": "Written", "job": "Screenplay", "popularity": 4},
                            {"id": 565, "media_type": "movie", "title": "Composed", "job": "Original Music Composer", "popularity": 3}
                          ]
                        }
                        """, org.springframework.http.MediaType.APPLICATION_JSON));

        ExternalPersonWorksProvider.PersonWorks works = client.findPersonWorks("287", "pt-BR");

        assertThat(works.items()).extracting(work -> work.media().externalId())
                .containsExactly("550", "562", "807", "563", "564", "565", "550");
        assertThat(works.items().getFirst().role()).isEqualTo(CreditRole.ACTOR);
        assertThat(works.items().getFirst().characterName()).isEqualTo("Tyler Durden");
        assertThat(works.items().get(1).media().type()).isEqualTo(MediaType.SERIES);
        assertThat(works.items().get(2).role()).isEqualTo(CreditRole.DIRECTOR);
        assertThat(works.items().get(3).role()).isEqualTo(CreditRole.PRODUCER);
        assertThat(works.items().get(4).role()).isEqualTo(CreditRole.SCREENWRITER);
        assertThat(works.items().get(5).role()).isEqualTo(CreditRole.COMPOSER);
        assertThat(works.items().getLast().role()).isEqualTo(CreditRole.PRODUCER);
        server.verify();
    }

    @Test
    void resolvesTitleImdbIdentityFromExternalIds() {
        server.expect(requestTo(startsWith(BASE_URL + "/movie/550/external_ids")))
                .andExpect(queryParam("api_key", "api-key"))
                .andRespond(withSuccess("""
                        {"id": 550, "imdb_id": "tt0137523"}
                        """, org.springframework.http.MediaType.APPLICATION_JSON));

        assertThat(client.findImdbId(MediaType.MOVIE, "550")).contains("tt0137523");
        server.verify();
    }

    @Test
    void mapsRegionalJustWatchProviders() {
        server.expect(requestTo(startsWith(BASE_URL + "/movie/550/watch/providers")))
                .andExpect(queryParam("api_key", "api-key"))
                .andRespond(withSuccess("""
                        {
                          "results": {
                            "BR": {
                              "link": "https://www.themoviedb.org/movie/550/watch?locale=BR",
                              "flatrate": [{
                                "provider_id": 8,
                                "provider_name": "Netflix",
                                "logo_path": "/netflix.jpg",
                                "display_priority": 1
                              }],
                              "rent": [{
                                "provider_id": 2,
                                "provider_name": "Apple TV",
                                "logo_path": "/apple.jpg",
                                "display_priority": 2
                              }]
                            }
                          }
                        }
                        """, org.springframework.http.MediaType.APPLICATION_JSON));

        ExternalAvailability availability = client.findWatchProviders(MediaType.MOVIE, "550", "BR");

        assertThat(availability.attribution()).isEqualTo("JustWatch");
        assertThat(availability.offers()).extracting(ExternalAvailability.Offer::type)
                .containsExactly(ExternalOfferType.SUBSCRIPTION, ExternalOfferType.RENT);
        assertThat(availability.offers().getFirst().providerName()).isEqualTo("Netflix");
        assertThat(availability.offers().getFirst().logoUrl()).endsWith("/w92/netflix.jpg");
        server.verify();
    }
}
