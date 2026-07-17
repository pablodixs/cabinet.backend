package com.scriptles.cabinet.media.external;

import com.scriptles.cabinet.media.config.ExternalApiProperties;
import com.scriptles.cabinet.media.enums.CreditRole;
import com.scriptles.cabinet.media.enums.ExternalOfferType;
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
                            "artist-credit": [{
                              "name": "An Artist",
                              "artist": {"id": "artist-id", "name": "An Artist"}
                            }]
                          }]
                        }
                        """, org.springframework.http.MediaType.APPLICATION_JSON));

        List<ExternalMedia> results = client.search(MediaType.TRACK, "song", "pt-BR", 0, 20);

        assertThat(results).singleElement().satisfies(track -> {
            assertThat(track.type()).isEqualTo(MediaType.TRACK);
            assertThat(track.externalId()).isEqualTo("recording-id");
            assertThat(track.title()).isEqualTo("A Song");
            assertThat(track.creator()).isEqualTo("An Artist");
            assertThat(track.credits()).singleElement().satisfies(credit -> {
                assertThat(credit.externalId()).isEqualTo("artist-id");
                assertThat(credit.name()).isEqualTo("An Artist");
                assertThat(credit.role()).isEqualTo(CreditRole.ARTIST);
            });
            assertThat(track.durationSeconds()).isEqualTo(178);
            assertThat(track.releaseDate()).hasToString("2020-05-03");
        });
        server.verify();
    }

    @Test
    void searchesAlbumsWithCoverUrls() {
        server.expect(requestTo(startsWith(BASE_URL + "/release-group")))
                .andExpect(queryParam("fmt", "json"))
                .andExpect(queryParam("query", "album"))
                .andExpect(queryParam("offset", "0"))
                .andExpect(queryParam("limit", "20"))
                .andRespond(withSuccess("""
                        {
                          "release-groups": [{
                            "id": "album-id",
                            "title": "An Album",
                            "artist-credit": [{"name": "An Artist"}]
                          }]
                        }
                        """, org.springframework.http.MediaType.APPLICATION_JSON));

        List<ExternalMedia> results = client.search(MediaType.ALBUM, "album", "pt-BR", 0, 20);

        assertThat(results).singleElement().satisfies(album -> {
            assertThat(album.type()).isEqualTo(MediaType.ALBUM);
            assertThat(album.coverUrl()).isEqualTo("https://images.test/cover.jpg");
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

    @Test
    void resolvesArtistWikidataIdentityFromUrlRelations() {
        server.expect(requestTo(startsWith(BASE_URL + "/artist/artist-id")))
                .andExpect(queryParam("fmt", "json"))
                .andExpect(queryParam("inc", "url-rels"))
                .andRespond(withSuccess("""
                        {
                          "id": "artist-id",
                          "relations": [{
                            "type": "wikidata",
                            "url": {"resource": "https://www.wikidata.org/wiki/Q51552"}
                          }]
                        }
                        """, org.springframework.http.MediaType.APPLICATION_JSON));

        assertThat(client.findArtistWikidataId("artist-id")).contains("Q51552");
        server.verify();
    }

    @Test
    void browsesReleaseGroupsAndReportsTruncation() {
        server.expect(requestTo(startsWith(BASE_URL + "/release-group")))
                .andExpect(queryParam("fmt", "json"))
                .andExpect(queryParam("artist", "artist-id"))
                .andExpect(queryParam("inc", "artist-credits"))
                .andExpect(queryParam("release-group-status", "website-default"))
                .andExpect(queryParam("offset", "0"))
                .andExpect(queryParam("limit", "100"))
                .andExpect(header("User-Agent", "cabinet-test/1.0"))
                .andRespond(withSuccess("""
                        {
                          "release-group-count": 101,
                          "release-groups": [
                            {"id": "old", "title": "Old", "first-release-date": "2000",
                             "artist-credit": [{"name": "An Artist"}]},
                            {"id": "new", "title": "New", "first-release-date": "2024-03-01",
                             "artist-credit": [{"name": "An Artist"}]}
                          ]
                        }
                        """, org.springframework.http.MediaType.APPLICATION_JSON));

        ExternalPersonWorksProvider.PersonWorks works = client.findPersonWorks("artist-id", "pt-BR");

        assertThat(works.incomplete()).isTrue();
        assertThat(works.items()).extracting(work -> work.media().externalId())
                .containsExactly("new", "old");
        assertThat(works.items()).allSatisfy(work -> assertThat(work.media().type())
                .isEqualTo(MediaType.ALBUM));
        server.verify();
    }

    @Test
    void mapsRecordingStreamingAndPurchaseRelations() {
        server.expect(requestTo(startsWith(BASE_URL + "/recording/recording-id")))
                .andExpect(queryParam("fmt", "json"))
                .andExpect(queryParam("inc", "releases+url-rels"))
                .andRespond(withSuccess("""
                        {
                          "id": "recording-id",
                          "relations": [
                            {
                              "type": "free streaming",
                              "url": {"resource": "https://open.spotify.com/track/example"}
                            },
                            {
                              "type": "purchase for download",
                              "url": {"resource": "https://artist.bandcamp.com/track/example"}
                            },
                            {
                              "type": "official homepage",
                              "url": {"resource": "https://example.com"}
                            }
                          ]
                        }
                        """, org.springframework.http.MediaType.APPLICATION_JSON));

        ExternalAvailability availability = client.findListenAndBuyLinks(MediaType.TRACK, "recording-id");

        assertThat(availability.attribution()).isEqualTo("MusicBrainz");
        assertThat(availability.offers()).extracting(ExternalAvailability.Offer::providerName)
                .containsExactly("Spotify", "Bandcamp");
        assertThat(availability.offers()).extracting(ExternalAvailability.Offer::type)
                .containsExactly(ExternalOfferType.STREAM, ExternalOfferType.BUY_DOWNLOAD);
        server.verify();
    }

    @Test
    void fallsBackToReleaseLinksForARecording() {
        server.expect(requestTo(startsWith(BASE_URL + "/recording/recording-id")))
                .andExpect(queryParam("fmt", "json"))
                .andExpect(queryParam("inc", "releases+url-rels"))
                .andRespond(withSuccess("""
                        {
                          "id": "recording-id",
                          "relations": [],
                          "releases": [
                            {"id": "release-id", "status": "Official", "date": "2024-01-01"}
                          ]
                        }
                        """, org.springframework.http.MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith(BASE_URL + "/release/release-id")))
                .andExpect(queryParam("fmt", "json"))
                .andExpect(queryParam("inc", "url-rels"))
                .andRespond(withSuccess("""
                        {
                          "id": "release-id",
                          "relations": [{
                            "type": "streaming",
                            "url": {"resource": "https://music.apple.com/br/album/example"}
                          }]
                        }
                        """, org.springframework.http.MediaType.APPLICATION_JSON));

        ExternalAvailability availability = client.findListenAndBuyLinks(MediaType.TRACK, "recording-id");

        assertThat(availability.offers()).singleElement().satisfies(offer -> {
            assertThat(offer.providerName()).isEqualTo("Apple Music");
            assertThat(offer.type()).isEqualTo(ExternalOfferType.STREAM);
        });
        server.verify();
    }
}
