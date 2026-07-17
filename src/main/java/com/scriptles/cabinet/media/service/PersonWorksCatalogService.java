package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.external.ExternalMediaException;
import com.scriptles.cabinet.media.external.ExternalPersonWorksProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PersonWorksCatalogService {
    private static final Duration HIT_TTL = Duration.ofHours(6);
    private static final Duration MISS_TTL = Duration.ofMinutes(15);
    private static final Duration STALE_TTL = Duration.ofDays(7);
    private static final int MAX_CACHE_ENTRIES = 500;

    private final Map<ExternalSource, ExternalPersonWorksProvider> providers;
    private final Clock clock;
    private final Map<CacheKey, CacheEntry> cache = new ConcurrentHashMap<>();

    @Autowired
    public PersonWorksCatalogService(List<ExternalPersonWorksProvider> providers) {
        this(providers, Clock.systemUTC());
    }

    PersonWorksCatalogService(List<ExternalPersonWorksProvider> providers, Clock clock) {
        this.providers = providers.stream().collect(Collectors.toUnmodifiableMap(
                ExternalPersonWorksProvider::source,
                Function.identity()
        ));
        this.clock = clock;
    }

    public CatalogResult find(ExternalSource source, String personExternalId, String language) {
        CacheKey key = new CacheKey(source, personExternalId, normalizeLanguage(language));
        Instant now = Instant.now(clock);
        CacheEntry cached = cache.get(key);
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return new CatalogResult(cached.items(), cached.incomplete());
        }

        ExternalPersonWorksProvider provider = providers.get(source);
        if (provider == null) {
            return new CatalogResult(List.of(), true);
        }
        try {
            ExternalPersonWorksProvider.PersonWorks result = provider.findPersonWorks(
                    personExternalId, language);
            Duration ttl = result.items().isEmpty() ? MISS_TTL : HIT_TTL;
            CacheEntry entry = new CacheEntry(
                    List.copyOf(result.items()),
                    result.incomplete(),
                    now.plus(ttl),
                    now.plus(STALE_TTL)
            );
            prune(now);
            cache.put(key, entry);
            return new CatalogResult(entry.items(), entry.incomplete());
        } catch (ExternalMediaException exception) {
            if (cached != null && cached.staleUntil().isAfter(now)) {
                return new CatalogResult(cached.items(), true);
            }
            return new CatalogResult(List.of(), true);
        }
    }

    private void prune(Instant now) {
        if (cache.size() < MAX_CACHE_ENTRIES) {
            return;
        }
        cache.entrySet().removeIf(entry -> entry.getValue().staleUntil().isBefore(now));
        if (cache.size() >= MAX_CACHE_ENTRIES) {
            cache.clear();
        }
    }

    private String normalizeLanguage(String language) {
        return language == null ? "" : language.trim().toLowerCase(Locale.ROOT);
    }

    public record CatalogResult(
            List<ExternalPersonWorksProvider.Work> items,
            boolean incomplete
    ) {
    }

    private record CacheKey(ExternalSource source, String personExternalId, String language) {
    }

    private record CacheEntry(
            List<ExternalPersonWorksProvider.Work> items,
            boolean incomplete,
            Instant expiresAt,
            Instant staleUntil
    ) {
    }
}
