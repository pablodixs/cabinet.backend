package com.scriptles.cabinet.media.external;

import com.scriptles.cabinet.media.config.OmdbProperties;
import com.scriptles.cabinet.media.enums.ExternalRatingMetric;
import com.scriptles.cabinet.media.enums.ExternalSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OmdbClientTest {
    private static final String BASE_URL = "https://omdb.test";

    private MockRestServiceServer server;
    private OmdbClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OmdbClient(builder, new OmdbProperties(BASE_URL, "omdb-key"));
    }

    @Test
    void mapsImdbTomatometerAndMetascoreWithTheirOriginalScales() {
        server.expect(requestTo(startsWith(BASE_URL + "/")))
                .andExpect(queryParam("apikey", "omdb-key"))
                .andExpect(queryParam("i", "tt0133093"))
                .andRespond(withSuccess("""
                        {
                          "Response": "True",
                          "imdbID": "tt0133093",
                          "Ratings": [
                            {"Source": "Internet Movie Database", "Value": "8.7/10"},
                            {"Source": "Rotten Tomatoes", "Value": "83%"},
                            {"Source": "Metacritic", "Value": "73/100"}
                          ],
                          "Metascore": "73"
                        }
                        """, org.springframework.http.MediaType.APPLICATION_JSON));

        var ratings = client.findRatings("tt0133093");

        assertThat(ratings).hasSize(3);
        assertThat(ratings).filteredOn(value -> value.metric() == ExternalRatingMetric.TOMATOMETER)
                .singleElement().satisfies(value -> {
                    assertThat(value.source()).isEqualTo(ExternalSource.ROTTEN_TOMATOES);
                    assertThat(value.value()).isEqualTo(83.0);
                    assertThat(value.scale()).isEqualTo(100);
                });
        assertThat(ratings).filteredOn(value -> value.metric() == ExternalRatingMetric.METASCORE)
                .singleElement().satisfies(value -> assertThat(value.displayValue()).isEqualTo("73/100"));
        server.verify();
    }

    @Test
    void treatsUnknownTitleAsAnEmptyRatingSet() {
        server.expect(requestTo(startsWith(BASE_URL + "/")))
                .andRespond(withSuccess("""
                        {"Response":"False","Error":"Incorrect IMDb ID."}
                        """, org.springframework.http.MediaType.APPLICATION_JSON));

        assertThat(client.findRatings("tt0000000")).isEmpty();
        server.verify();
    }

    @Test
    void rejectsProviderAuthenticationErrors() {
        server.expect(requestTo(startsWith(BASE_URL + "/")))
                .andRespond(withSuccess("""
                        {"Response":"False","Error":"Invalid API key!"}
                        """, org.springframework.http.MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.findRatings("tt0133093"))
                .isInstanceOf(ExternalMediaException.class)
                .hasMessageContaining("Invalid API key");
        server.verify();
    }
}
