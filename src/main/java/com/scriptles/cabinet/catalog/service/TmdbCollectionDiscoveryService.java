package com.scriptles.cabinet.catalog.service;

import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.external.TmdbClient;
import com.scriptles.cabinet.media.external.TmdbCollectionMembership;
import com.scriptles.cabinet.media.repository.ExternalReferenceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Slf4j
public class TmdbCollectionDiscoveryService {
    private final ExternalReferenceRepository externalReferenceRepository;
    private final TmdbClient tmdbClient;
    private final TmdbCollectionDiscoveryWriter writer;
    private final Executor executor;
    private final String locale;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicReference<DiscoveryStatus> status = new AtomicReference<>(
            new DiscoveryStatus(false, 0, 0, 0, 0, 0));

    public TmdbCollectionDiscoveryService(
            ExternalReferenceRepository externalReferenceRepository,
            TmdbClient tmdbClient,
            TmdbCollectionDiscoveryWriter writer,
            @Qualifier("externalInfoTaskExecutor") Executor executor,
            @Value("${catalog.collections.tmdb-sync.locale:pt-BR}") String locale
    ) {
        this.externalReferenceRepository = externalReferenceRepository;
        this.tmdbClient = tmdbClient;
        this.writer = writer;
        this.executor = executor;
        this.locale = locale;
    }

    public boolean startDiscovery() {
        if (!running.compareAndSet(false, true)) return false;
        status.set(new DiscoveryStatus(true, 0, 0, 0, 0, 0));
        try {
            executor.execute(this::discoverCollections);
            return true;
        } catch (RuntimeException failure) {
            running.set(false);
            status.set(new DiscoveryStatus(false, 0, 0, 0, 0, 1));
            throw failure;
        }
    }

    public DiscoveryStatus status() {
        return status.get();
    }

    private void discoverCollections() {
        int checkedMovies = 0;
        int moviesWithCollections = 0;
        int failures = 0;
        Set<String> discoveredCollectionIds = new HashSet<>();

        try {
            List<String> movieIds = externalReferenceRepository.findExternalIdsBySourceAndMediaType(
                    ExternalSource.TMDB, "MOVIE");
            status.set(new DiscoveryStatus(true, movieIds.size(), 0, 0, 0, 0));
            for (String movieId : movieIds) {
                try {
                    var membership = tmdbClient.findMovieCollectionMembership(movieId, locale);
                    if (membership.isPresent()) {
                        moviesWithCollections++;
                        TmdbCollectionMembership collection = membership.get();
                        if (!discoveredCollectionIds.contains(collection.externalId())) {
                            writer.ensureLinkedCollection(collection);
                            discoveredCollectionIds.add(collection.externalId());
                        }
                    }
                } catch (RuntimeException failure) {
                    failures++;
                    log.warn("Unable to discover TMDB collection for movie {}: {}", movieId,
                            failure.getMessage());
                } finally {
                    checkedMovies++;
                }
                status.set(new DiscoveryStatus(true, movieIds.size(), checkedMovies, moviesWithCollections,
                        discoveredCollectionIds.size(), failures));
            }
            log.info("TMDB collection discovery completed: {} of {} movies checked, {} memberships, "
                            + "{} collections linked, {} failures", checkedMovies, movieIds.size(),
                    moviesWithCollections, discoveredCollectionIds.size(), failures);
        } catch (RuntimeException failure) {
            failures++;
            log.error("TMDB collection discovery failed before completion", failure);
        } finally {
            running.set(false);
            DiscoveryStatus previous = status.get();
            status.set(new DiscoveryStatus(false, previous.totalMovies(), checkedMovies,
                    moviesWithCollections, discoveredCollectionIds.size(), failures));
        }
    }

    public record DiscoveryStatus(
            boolean running,
            int totalMovies,
            int checkedMovies,
            int moviesWithCollections,
            int collectionsLinked,
            int failures
    ) {
    }
}
