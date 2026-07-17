package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.external.ExternalMediaException;
import com.scriptles.cabinet.media.external.MusicBrainzClient;
import com.scriptles.cabinet.media.external.TmdbClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExternalArtistIdentityEnricher implements ArtistIdentityEnricher {
    private final TmdbClient tmdbClient;
    private final MusicBrainzClient musicBrainzClient;
    private final ConcurrentMap<IdentityKey, Optional<String>> cache = new ConcurrentHashMap<>();

    @Override
    public Optional<String> findWikidataId(ExternalSource source, String externalId) {
        if (source == null || externalId == null || externalId.isBlank()) {
            return Optional.empty();
        }
        IdentityKey key = new IdentityKey(source, externalId.trim());
        Optional<String> cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        Optional<String> resolved = lookup(key);
        resolved.ifPresent(ignored -> cache.putIfAbsent(key, resolved));
        return resolved;
    }

    private Optional<String> lookup(IdentityKey key) {
        try {
            return switch (key.source()) {
                case TMDB -> tmdbClient.findPersonWikidataId(key.externalId());
                case MUSICBRAINZ -> musicBrainzClient.findArtistWikidataId(key.externalId());
                default -> Optional.empty();
            };
        } catch (ExternalMediaException exception) {
            log.warn("Unable to resolve Wikidata identity for {} artist {}",
                    key.source(), key.externalId(), exception);
            return Optional.empty();
        }
    }

    private record IdentityKey(ExternalSource source, String externalId) {
    }
}
