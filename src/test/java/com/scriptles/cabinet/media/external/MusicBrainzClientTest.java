package com.scriptles.cabinet.media.external;

import com.scriptles.cabinet.media.config.ExternalApiProperties;
import com.scriptles.cabinet.media.enums.MediaType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MusicBrainzClientTest {
    private static final String BASE_URL = "https://musicbrainz.test/ws/2";

    private MockRestServiceServer server;
    private MusicBrainzClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
        AlbumCoverService albumCoverService = new AlbumCoverService(null, null) {
            @Override
            public String findCoverUrl(String releaseGroupId) {
                return "https://images.test/cover.jpg";
            }
        };

        ExternalApiProperties properties = new ExternalApiProperties(
                null,
                null,
                new ExternalApiProperties.MusicBrainz(BASE_URL, "cabinet-test/1.0"),
                null,
                null
        );
        client = new MusicBrainzClient(restClientBuilder, properties, albumCoverService);
    }

    @Test
    void searchesRecordingsAndMapsDuration() {
        server.expect(requestTo(startsWith(BASE_URL + "/recording")))
                .andExpect(queryParam("fmt", "json"))
                .andExpect(queryParam("query", "song"))
                .andExpect(queryParam("offset", "0"))
                .andExpect(queryParam("limit", "20"))
                .andExpect(header("User-Agent", "cabinet-test/1.0"))
                .andRespond(withSuccess("""
                        {
                          "recordings": [{
                            "id": "recording-id",
                            "title": "A Song",
                            "length": 178999,
                            "first-release-date": "2020-05-03",
                            "artist-credit": [{"name": "An Artist"}]
                          }]
                        }
                        """, org.springframework.http.MediaType.APPLICATION_JSON));

        List<ExternalMedia> results = client.search(MediaType.TRACK, "song", "pt-BR", 0, 20);

        assertThat(results).singleElement().satisfies(track -> {
            assertThat(track.type()).isEqualTo(MediaType.TRACK);
            assertThat(track.externalId()).isEqualTo("recording-id");
            assertThat(track.title()).isEqualTo("A Song");
            assertThat(track.creator()).isEqualTo("An Artist");
            assertThat(track.durationSeconds()).isEqualTo(178);
            assertThat(track.releaseDate()).hasToString("2020-05-03");
        });
        server.verify();
    }

    @Test
    void mapsAlbumWikidataAndUsesOfficialReleaseWithRecordingLengthFallback() {
        server.expect(requestTo(startsWith(BASE_URL + "/release-group/album-id")))
                .andExpect(queryParam("fmt", "json"))
                .andExpect(queryParam("inc", "artist-credits+genres+releases+media+url-rels"))
                .andRespond(withSuccess("""
                        {
                          "id": "album-id",
                          "title": "An Album",
                          "first-release-date": "2020",
                          "primary-type": "Album",
                          "secondary-types": ["Soundtrack"],
                          "artist-credit": [{"name": "An Artist"}],
                          "relations": [{
                            "type": "wikidata",
                            "url": {"resource": "https://www.wikidata.org/wiki/Q12345"}
                          }],
                          "releases": [
                            {"id": "bootleg", "status": "Bootleg", "media": [{"track-count": 99}]},
                            {"id": "official-short", "status": "Official", "media": [{"track-count": 9}]},
                            {"id": "official-complete", "status": "Official", "media": [{"track-count": 10}]}
                          ]
                        }
                        """, org.springframework.http.MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith(BASE_URL + "/release/official-complete")))
                .andExpect(queryParam("fmt", "json"))
                .andExpect(queryParam("inc", "recordings"))
                .andRespond(withSuccess("""
                        {
                          "media": [{
                            "position": 2,
                            "tracks": [{
                              "position": 3,
                              "recording": {
                                "id": "recording-id",
                                "title": "A Song",
                                "length": 123456
                              }
                            }]
                          }]
                        }
                        """, org.springframework.http.MediaType.APPLICATION_JSON));

        ExternalMedia album = client.findById(MediaType.ALBUM, "album-id").orElseThrow();

        assertThat(album.wikidataId()).isEqualTo("Q12345");
        assertThat(album.albumType()).isEqualTo("Soundtrack");
        assertThat(album.numberOfTracks()).isEqualTo(1);
        assertThat(album.tracks()).singleElement().satisfies(track -> {
            assertThat(track.title()).isEqualTo("A Song");
            assertThat(track.discNumber()).isEqualTo(2);
            assertThat(track.trackNumber()).isEqualTo(3);
            assertThat(track.durationSeconds()).isEqualTo(123);
        });
        server.verify();
    }
}
