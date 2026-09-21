package com.scriptles.cabinet.catalog.service;

import com.scriptles.cabinet.catalog.collection.CollectionType;
import com.scriptles.cabinet.catalog.entity.Collection;
import com.scriptles.cabinet.catalog.entity.CollectionExternalReference;
import com.scriptles.cabinet.catalog.repository.CollectionExternalReferenceRepository;
import com.scriptles.cabinet.catalog.repository.CollectionRepository;
import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.media.catalog.CatalogImportFacade;
import com.scriptles.cabinet.media.dto.request.MediaTarget;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.enums.SupportedLocale;
import com.scriptles.cabinet.media.external.ExternalMediaException;
import com.scriptles.cabinet.media.external.TmdbClient;
import com.scriptles.cabinet.media.external.TmdbCollectionSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CollectionEnrichmentService {
    private final CollectionRepository collectionRepository;
    private final CollectionExternalReferenceRepository externalReferenceRepository;
    private final TmdbClient tmdbClient;
    private final CatalogImportFacade catalogImportFacade;
    private final CollectionSyncWriter syncWriter;

    public int sync(UUID collectionId, String locale) {
        String normalizedLocale = SupportedLocale.from(locale).tag();
        Collection collection = collectionRepository.findById(collectionId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "COLLECTION_NOT_FOUND", "Collection was not found"));
        if (collection.getType() != CollectionType.FILM_SERIES) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "COLLECTION_TYPE_UNSUPPORTED", "Only film series can sync from TMDB");
        }

        CollectionExternalReference reference = externalReferenceRepository.findByCollectionId(collectionId).stream()
                .filter(item -> item.getProvider() == ExternalSource.TMDB)
                .findFirst()
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST, "TMDB_COLLECTION_REFERENCE_REQUIRED",
                        "Collection does not have a TMDB reference"));

        TmdbCollectionSnapshot snapshot = tmdbClient.findCollectionById(reference.getExternalId(), normalizedLocale);
        if (!snapshot.complete()) {
            throw new ExternalMediaException("TMDB collection contained one or more malformed movie parts");
        }

        // Keep the first occurrence and provider order if TMDB happens to repeat a part.
        LinkedHashMap<String, TmdbCollectionSnapshot.Movie> distinctMovies = new LinkedHashMap<>();
        for (TmdbCollectionSnapshot.Movie movie : snapshot.movies()) {
            distinctMovies.putIfAbsent(movie.externalId(), movie);
        }

        List<CollectionSyncWriter.MaterializedMovie> materializedMovies = new ArrayList<>();
        for (TmdbCollectionSnapshot.Movie movie : distinctMovies.values()) {
            CatalogImportFacade.Result result = catalogImportFacade.materialize(new MediaTarget(
                    null,
                    ExternalSource.TMDB,
                    movie.externalId(),
                    MediaType.MOVIE,
                    normalizedLocale
            ));
            materializedMovies.add(new CollectionSyncWriter.MaterializedMovie(
                    movie.externalId(), result.media().getId()));
        }

        syncWriter.reconcile(collectionId, reference.getExternalId(), snapshot,
                materializedMovies, Instant.now());
        return materializedMovies.size();
    }
}
