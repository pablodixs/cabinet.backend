package com.scriptles.cabinet.media.external;

import com.scriptles.cabinet.media.config.ExternalApiProperties;
import com.scriptles.cabinet.media.enums.MediaType;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ArtworkCatalogProviderTest {
    @Test
    void tmdbFiltersUnsupportedLanguagesAndOrdersPortugueseFirst() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ExternalApiProperties properties = new ExternalApiProperties(
                new ExternalApiProperties.Tmdb("https://tmdb.test/3", "api-key", ""),
                null, null, null, null);
        TmdbArtworkCatalogProvider provider = new TmdbArtworkCatalogProvider(builder, properties);
        server.expect(requestTo(startsWith("https://tmdb.test/3/movie/550/images")))
                .andExpect(queryParam("api_key", "api-key"))
                .andExpect(queryParam("include_image_language", "pt,en,null"))
                .andRespond(withSuccess("""
                        {
                          "posters": [
                            {"file_path":"/en.jpg","iso_639_1":"en","vote_count":100,"vote_average":9},
                            {"file_path":"/pt.jpg","iso_639_1":"pt","vote_count":1,"vote_average":5},
                            {"file_path":"/es.jpg","iso_639_1":"es","vote_count":500,"vote_average":10}
                          ],
                          "backdrops": [
                            {"file_path":"/bg.jpg","iso_639_1":null,"width":1920,"height":1080}
                          ]
                        }
                        """, org.springframework.http.MediaType.APPLICATION_JSON));

        ArtworkCatalog catalog = provider.find(MediaType.MOVIE, "550", "pt-BR");

        assertThat(catalog.covers()).extracting(ArtworkAsset::key)
                .containsExactly("/pt.jpg", "/en.jpg");
        assertThat(catalog.backdrops()).singleElement().satisfies(asset -> {
            assertThat(asset.url()).isEqualTo("https://image.tmdb.org/t/p/original/bg.jpg");
            assertThat(asset.previewUrl()).isEqualTo("https://image.tmdb.org/t/p/w780/bg.jpg");
        });
        server.verify();
    }

    @Test
    void coverArtArchiveReturnsOnlyFrontCovers() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ExternalApiProperties properties = new ExternalApiProperties(
                null, null,
                new ExternalApiProperties.MusicBrainz("https://musicbrainz.test", "cabinet-test"),
                null, null);
        CoverArtArchiveArtworkCatalogProvider provider =
                new CoverArtArchiveArtworkCatalogProvider(builder, properties);
        server.expect(requestTo("https://coverartarchive.org/release-group/release-group-id"))
                .andRespond(withSuccess("""
                        {
                          "images": [
                            {"id": "front-id", "front": true, "image": "https://images/front.jpg",
                             "thumbnails": {"500": "https://images/front-500.jpg"}},
                            {"id": "back-id", "front": false, "image": "https://images/back.jpg",
                             "thumbnails": {"500": "https://images/back-500.jpg"}}
                          ]
                        }
                        """, org.springframework.http.MediaType.APPLICATION_JSON));

        ArtworkCatalog catalog = provider.find(MediaType.ALBUM, "release-group-id", "pt-BR");

        assertThat(catalog.covers()).singleElement().satisfies(asset -> {
            assertThat(asset.key()).isEqualTo("front-id");
            assertThat(asset.previewUrl()).isEqualTo("https://images/front-500.jpg");
        });
        assertThat(catalog.backdrops()).isEmpty();
        server.verify();
    }
}
