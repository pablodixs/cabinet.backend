package com.scriptles.cabinet.media.external;

import java.time.LocalDate;
import java.util.List;

public record TmdbCollectionSnapshot(
        String externalId,
        String name,
        String overview,
        String posterUrl,
        String backdropUrl,
        List<Movie> movies,
        boolean complete
) {
    public TmdbCollectionSnapshot {
        movies = List.copyOf(movies);
    }

    public record Movie(
            String externalId,
            String title,
            String originalTitle,
            LocalDate releaseDate,
            String posterUrl
    ) {
    }
}
