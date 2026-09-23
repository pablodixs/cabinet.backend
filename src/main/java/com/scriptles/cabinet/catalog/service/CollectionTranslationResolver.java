package com.scriptles.cabinet.catalog.service;

import com.scriptles.cabinet.catalog.entity.Collection;
import com.scriptles.cabinet.catalog.entity.CollectionTranslation;
import com.scriptles.cabinet.catalog.repository.CollectionTranslationRepository;
import com.scriptles.cabinet.media.translation.CatalogLocaleResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CollectionTranslationResolver {
    private final CollectionTranslationRepository repository;
    private final CatalogLocaleResolver localeResolver;

    public ResolvedCollection resolve(Collection collection, String locale) {
        return resolveFrom(collection, localeResolver.normalize(locale),
                repository.findAllByCollectionIdIn(List.of(collection.getId())));
    }

    public Map<UUID, ResolvedCollection> resolveAll(List<Collection> collections, String locale) {
        if (collections.isEmpty()) return Map.of();
        String normalized = localeResolver.normalize(locale);
        Map<UUID, List<CollectionTranslation>> stored = repository
                .findAllByCollectionIdIn(collections.stream().map(Collection::getId).toList()).stream()
                .collect(Collectors.groupingBy(value -> value.getCollection().getId()));
        Map<UUID, ResolvedCollection> result = new LinkedHashMap<>();
        for (Collection collection : collections) {
            result.put(collection.getId(), resolveFrom(collection, normalized,
                    stored.getOrDefault(collection.getId(), List.of())));
        }
        return result;
    }

    private ResolvedCollection resolveFrom(Collection collection, String requested,
            List<CollectionTranslation> stored) {
        List<CollectionTranslation> candidates = new ArrayList<>(stored);
        List<String> fallback = localeResolver.fallbackChain(requested);
        candidates.sort(Comparator.comparingInt(value -> {
            int index = fallback.indexOf(value.getLocale());
            return index < 0 ? fallback.size() : index;
        }));
        String canonical = collection.getDefaultLocale() == null
                ? CatalogLocaleResolver.DEFAULT_LOCALE : collection.getDefaultLocale();
        Field title = first(candidates, CollectionTranslation::getTitle)
                .orElse(collection.getTitle(), canonical);
        Field description = first(candidates, CollectionTranslation::getDescription)
                .orElse(collection.getDescription(), canonical);
        Field poster = first(candidates, CollectionTranslation::getPosterUrl)
                .orElse(collection.getPosterUrl(), canonical);
        Field backdrop = first(candidates, CollectionTranslation::getBackdropUrl)
                .orElse(collection.getBackdropUrl(), canonical);
        String resolved = title.locale() == null ? canonical : title.locale();
        boolean fallbackUsed = !requested.equals(resolved)
                || differs(description, requested) || differs(poster, requested) || differs(backdrop, requested);
        return new ResolvedCollection(title.value(), description.value(), poster.value(), backdrop.value(),
                requested, resolved, fallbackUsed);
    }

    private Field first(List<CollectionTranslation> values, Function<CollectionTranslation, String> getter) {
        return values.stream().map(value -> new Field(getter.apply(value), value.getLocale()))
                .filter(value -> value.value() != null && !value.value().isBlank())
                .findFirst().orElse(Field.empty());
    }

    private boolean differs(Field value, String locale) {
        return value.value() != null && !value.value().isBlank() && !locale.equals(value.locale());
    }

    private record Field(String value, String locale) {
        static Field empty() { return new Field(null, null); }
        Field orElse(String value, String locale) {
            return this.value == null || this.value.isBlank() ? new Field(value, locale) : this;
        }
    }

    public record ResolvedCollection(String title, String description, String posterUrl, String backdropUrl,
                                     String requestedLocale, String resolvedLocale, boolean fallback) {}
}
