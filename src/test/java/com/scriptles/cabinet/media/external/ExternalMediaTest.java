package com.scriptles.cabinet.media.external;

import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalMediaTest {
    @Test
    void enrichmentKeepsPrimaryGenreAndAddsMissingWikidataGenre() {
        ExternalMedia media = media(List.of(
                new ExternalMedia.ExternalGenre("18", "Drama", ExternalSource.TMDB)
        ));

        ExternalMedia enriched = media.withEnrichment("logo", List.of(
                new ExternalMedia.ExternalGenre("Q130232", "Drama", ExternalSource.WIKIDATA),
                new ExternalMedia.ExternalGenre("Q157443", "Comedy", ExternalSource.WIKIDATA)
        ));

        assertThat(enriched.logoUrl()).isEqualTo("logo");
        assertThat(enriched.genres()).extracting(ExternalMedia.ExternalGenre::name)
                .containsExactly("Drama", "Comedy");
        assertThat(enriched.genres().getFirst().source()).isEqualTo(ExternalSource.TMDB);
    }

    private ExternalMedia media(List<ExternalMedia.ExternalGenre> genres) {
        return new ExternalMedia(
                ExternalSource.TMDB, "550", MediaType.MOVIE, "Fight Club", "Fight Club", null, null,
                null, null, null, null, "en", "US", null, null, null, null, null, null, 139, null, null,
                null, null, null, null, null, null, "David Fincher", null, null,
                genres, List.of(), List.of(), List.of()
        );
    }
}
