package com.scriptles.cabinet.media.catalog;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

@Component
public class CatalogSnapshotCache {
    private final Cache<Key, CatalogSnapshot> snapshots = Caffeine.newBuilder()
            .maximumSize(20_000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .recordStats()
            .build();
    private final Cache<Key, Boolean> missing = Caffeine.newBuilder()
            .maximumSize(5_000)
            .expireAfterWrite(Duration.ofMinutes(2))
            .recordStats()
            .build();

    public Optional<CatalogSnapshot> get(ExternalSource source, MediaType type, String externalId, String locale) {
        return Optional.ofNullable(snapshots.getIfPresent(new Key(source, type, externalId, locale)));
    }

    public boolean isMissing(ExternalSource source, MediaType type, String externalId, String locale) {
        return missing.getIfPresent(new Key(source, type, externalId, locale)) != null;
    }

    public Optional<CatalogSnapshot> getOrLoad(
            ExternalSource source,
            MediaType type,
            String externalId,
            String locale,
            Supplier<Optional<CatalogSnapshot>> loader
    ) {
        Key key = new Key(source, type, externalId, locale);
        if (missing.getIfPresent(key) != null) return Optional.empty();
        CatalogSnapshot loaded = snapshots.get(key, ignored -> loader.get().orElse(null));
        if (loaded == null) missing.put(key, Boolean.TRUE);
        return Optional.ofNullable(loaded);
    }

    public void put(CatalogSnapshot snapshot) {
        Key key = key(snapshot);
        missing.invalidate(key);
        snapshots.put(key, snapshot);
    }

    public void putMissing(ExternalSource source, MediaType type, String externalId, String locale) {
        missing.put(new Key(source, type, externalId, locale), Boolean.TRUE);
    }

    private Key key(CatalogSnapshot snapshot) {
        return new Key(
                snapshot.media().source(),
                snapshot.media().type(),
                snapshot.media().externalId(),
                snapshot.locale()
        );
    }

    private record Key(ExternalSource source, MediaType type, String externalId, String locale) {
    }
}
