package com.scriptles.cabinet.media.translation;

import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.MediaTranslation;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.TranslationStatus;
import com.scriptles.cabinet.media.repository.MediaTranslationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DefaultMediaTranslationResolver implements MediaTranslationResolver {
    private final MediaTranslationRepository repository;
    private final CatalogLocaleResolver localeResolver;

    @Override
    public ResolvedMediaTranslation resolve(Media media, String requestedLocale) {
        if (media == null) {
            throw new IllegalArgumentException("Media is required");
        }
        return resolveFrom(media, localeResolver.normalize(requestedLocale),
                repository.findAllByMediaId(media.getId()));
    }

    @Override
    public Map<UUID, ResolvedMediaTranslation> resolveAll(List<Media> mediaItems, String requestedLocale) {
        if (mediaItems == null || mediaItems.isEmpty()) {
            return Map.of();
        }
        String normalizedLocale = localeResolver.normalize(requestedLocale);
        List<UUID> ids = mediaItems.stream().map(Media::getId).filter(Objects::nonNull).distinct().toList();
        Map<UUID, List<MediaTranslation>> translations = ids.isEmpty()
                ? Map.of()
                : repository.findForResolution(ids).stream()
                        .collect(Collectors.groupingBy(
                                translation -> translation.getMedia().getId(),
                                LinkedHashMap::new,
                                Collectors.toList()
                        ));
        Map<UUID, ResolvedMediaTranslation> resolved = new LinkedHashMap<>();
        for (Media media : mediaItems) {
            resolved.put(media.getId(), resolveFrom(
                    media,
                    normalizedLocale,
                    translations.getOrDefault(media.getId(), List.of())
            ));
        }
        return Map.copyOf(resolved);
    }

    private ResolvedMediaTranslation resolveFrom(
            Media media,
            String requestedLocale,
            List<MediaTranslation> stored
    ) {
        List<MediaTranslation> candidates = new ArrayList<>(stored);
        candidates.sort(candidateOrder(requestedLocale));

        FieldValue title = first(candidates, MediaTranslation::getTitle);
        FieldValue description = first(candidates, MediaTranslation::getDescription);
        FieldValue tagline = first(candidates, MediaTranslation::getTagline);
        String canonicalLocale = nonBlank(media.getDefaultLocale())
                ? media.getDefaultLocale()
                : CatalogLocaleResolver.DEFAULT_LOCALE;

        title = title.orElse(firstNonBlank(media.getTitle(), media.getOriginalTitle()), canonicalLocale);
        description = description.orElse(media.getDescription(), canonicalLocale);
        tagline = tagline.orElse(media.getTagline(), canonicalLocale);

        String resolvedLocale = title.locale() == null ? canonicalLocale : title.locale();
        boolean fallback = !requestedLocale.equals(resolvedLocale)
                || differsFromRequested(description, requestedLocale)
                || differsFromRequested(tagline, requestedLocale);
        boolean partialFallback = requestedLocale.equals(resolvedLocale)
                && (differsFromRequested(description, requestedLocale)
                || differsFromRequested(tagline, requestedLocale));
        MediaTranslation primary = title.translation();
        return new ResolvedMediaTranslation(
                media.getId(),
                title.value(),
                description.value(),
                tagline.value(),
                requestedLocale,
                resolvedLocale,
                fallback,
                partialFallback,
                primary == null ? TranslationStatus.FALLBACK : primary.getTranslationStatus(),
                primary == null ? ExternalSource.MANUAL : primary.getSource()
        );
    }

    private Comparator<MediaTranslation> candidateOrder(String requestedLocale) {
        List<String> locales = localeResolver.fallbackChain(requestedLocale);
        return Comparator
                .comparingInt((MediaTranslation value) -> localeRank(value.getLocale(), locales))
                .thenComparingInt(value -> statusRank(value.getTranslationStatus()))
                .thenComparing(MediaTranslation::getLocale, Comparator.nullsLast(String::compareTo))
                .thenComparing(value -> value.getId() == null ? "" : value.getId().toString());
    }

    private int localeRank(String locale, List<String> locales) {
        int index = locales.indexOf(locale);
        return index < 0 ? locales.size() : index;
    }

    private int statusRank(TranslationStatus status) {
        if (status == TranslationStatus.AVAILABLE) return 0;
        if (status == TranslationStatus.PARTIAL) return 1;
        return 2;
    }

    private FieldValue first(List<MediaTranslation> candidates, Function<MediaTranslation, String> getter) {
        return candidates.stream()
                .filter(value -> usable(value.getTranslationStatus()))
                .map(value -> new FieldValue(getter.apply(value), value.getLocale(), value))
                .filter(value -> nonBlank(value.value()))
                .findFirst()
                .orElse(FieldValue.empty());
    }

    private boolean usable(TranslationStatus status) {
        return status == TranslationStatus.AVAILABLE || status == TranslationStatus.PARTIAL;
    }

    private boolean differsFromRequested(FieldValue value, String requestedLocale) {
        return nonBlank(value.value()) && !requestedLocale.equals(value.locale());
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (nonBlank(value)) return value;
        }
        return null;
    }

    private boolean nonBlank(String value) {
        return value != null && !value.isBlank();
    }

    private record FieldValue(String value, String locale, MediaTranslation translation) {
        static FieldValue empty() {
            return new FieldValue(null, null, null);
        }

        FieldValue orElse(String fallbackValue, String fallbackLocale) {
            return value == null || value.isBlank()
                    ? new FieldValue(fallbackValue, fallbackLocale, null)
                    : this;
        }
    }
}
