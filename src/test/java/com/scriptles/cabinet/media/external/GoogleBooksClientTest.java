package com.scriptles.cabinet.media.external;

import com.scriptles.cabinet.media.config.ExternalApiProperties;
import com.scriptles.cabinet.media.enums.CreditRole;
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

class GoogleBooksClientTest {
    private static final String BASE_URL = "https://books.test/v1";

    private MockRestServiceServer server;
    private GoogleBooksClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
        ExternalApiProperties properties = new ExternalApiProperties(
                null,
                new ExternalApiProperties.GoogleBooks(BASE_URL, "test-key"),
                null,
                null,
                null
        );
        client = new GoogleBooksClient(restClientBuilder, properties);
    }

    @Test
    void mapsAuthorsToStructuredCredits() {
        server.expect(requestTo(startsWith(BASE_URL + "/volumes")))
                .andExpect(queryParam("key", "test-key"))
                .andRespond(withSuccess("""
                        {
                          "items": [{
                            "id": "volume-id",
                            "volumeInfo": {
                              "title": "Good Omens",
                              "authors": ["Terry Pratchett", "Neil Gaiman"]
                            }
                          }]
                        }
                        """, org.springframework.http.MediaType.APPLICATION_JSON));

        ExternalMedia book = client.search(MediaType.BOOK, "good omens", "en", 0, 20).getFirst();

        assertThat(book.creator()).isEqualTo("Terry Pratchett, Neil Gaiman");
        assertThat(book.credits()).extracting(
                ExternalMedia.ExternalCredit::name,
                ExternalMedia.ExternalCredit::role,
                ExternalMedia.ExternalCredit::position
        ).containsExactly(
                org.assertj.core.groups.Tuple.tuple("Terry Pratchett", CreditRole.AUTHOR, 0),
                org.assertj.core.groups.Tuple.tuple("Neil Gaiman", CreditRole.AUTHOR, 1)
        );
        server.verify();
    }
}
