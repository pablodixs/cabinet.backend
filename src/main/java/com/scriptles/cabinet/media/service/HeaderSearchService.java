package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.media.dto.response.HeaderSearchItemResponse;
import com.scriptles.cabinet.media.dto.response.HeaderSearchResponse;
import com.scriptles.cabinet.catalog.domain.CatalogEntityStatus;
import com.scriptles.cabinet.catalog.entity.Collection;
import com.scriptles.cabinet.catalog.entity.Franchise;
import com.scriptles.cabinet.catalog.repository.CollectionRepository;
import com.scriptles.cabinet.catalog.service.CollectionTranslationResolver;
import com.scriptles.cabinet.catalog.repository.FranchiseRepository;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.Person;
import com.scriptles.cabinet.media.enums.HeaderSearchEntityType;
import com.scriptles.cabinet.media.enums.HeaderSearchScope;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.media.repository.PersonRepository;
import com.scriptles.cabinet.media.translation.CatalogLocaleResolver;
import com.scriptles.cabinet.media.translation.MediaTranslationResolver;
import com.scriptles.cabinet.media.translation.ResolvedMediaTranslation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HeaderSearchService {
    private static final int RESULT_LIMIT = 5;

    private final MediaRepository mediaRepository;
    private final PersonRepository personRepository;
    private final MediaCreditService mediaCreditService;
    private final MediaTranslationResolver translationResolver;
    private final CatalogLocaleResolver localeResolver;
    private final UserArtworkResolver userArtworkResolver;
    private CollectionRepository collectionRepository;
    private FranchiseRepository franchiseRepository;
    private CollectionTranslationResolver collectionTranslationResolver;

    @org.springframework.beans.factory.annotation.Autowired
    void setCatalogRepositories(CollectionRepository collections, FranchiseRepository franchises) { this.collectionRepository = collections; this.franchiseRepository = franchises; }
    @org.springframework.beans.factory.annotation.Autowired
    void setCollectionTranslationResolver(CollectionTranslationResolver resolver) { this.collectionTranslationResolver = resolver; }

    public HeaderSearchResponse search(String query, HeaderSearchScope scope, MediaType type) {
        return search(query, scope, type, CatalogLocaleResolver.DEFAULT_LOCALE);
    }

    public HeaderSearchResponse search(
            String query,
            HeaderSearchScope scope,
            MediaType type,
            String locale
    ) {
        return search(query, scope, type, locale, null);
    }

    public HeaderSearchResponse search(
            String query,
            HeaderSearchScope scope,
            MediaType type,
            String locale,
            UUID viewerId
    ) {
        String trimmedQuery = query.trim();
        String requestedLocale = localeResolver.normalize(locale);
        List<RankedItem> rankedItems = new ArrayList<>(RESULT_LIMIT * 2);

        if (scope == HeaderSearchScope.ALL || scope == HeaderSearchScope.MEDIA) {
            addMedia(rankedItems, trimmedQuery, type, requestedLocale, viewerId);
        }
        if (scope != HeaderSearchScope.MEDIA && scope != HeaderSearchScope.COLLECTION && scope != HeaderSearchScope.FRANCHISE) {
            addArtists(rankedItems, trimmedQuery, type);
        }
        if (collectionRepository != null && (scope == HeaderSearchScope.ALL || scope == HeaderSearchScope.COLLECTION)) addCollections(rankedItems, trimmedQuery, requestedLocale);
        if (franchiseRepository != null && (scope == HeaderSearchScope.ALL || scope == HeaderSearchScope.FRANCHISE)) addFranchises(rankedItems, trimmedQuery);

        List<HeaderSearchItemResponse> items = rankedItems.stream()
                .sorted(Comparator.comparingInt(RankedItem::relevance)
                        .thenComparing(RankedItem::normalizedTitle)
                        .thenComparing(item -> item.response().entityType())
                        .thenComparing(item -> item.response().id()))
                .limit(RESULT_LIMIT)
                .map(RankedItem::response)
                .toList();
        return new HeaderSearchResponse(items);
    }

    private void addCollections(List<RankedItem> target, String query, String locale) {
        for (Collection collection : collectionRepository.search(query, CatalogEntityStatus.ACTIVE, PageRequest.of(0, RESULT_LIMIT))) {
            var localized = collectionTranslationResolver == null ? null : collectionTranslationResolver.resolve(collection, locale);
            String title = localized == null ? collection.getTitle() : localized.title();
            String poster = localized == null ? collection.getPosterUrl() : localized.posterUrl();
            HeaderSearchItemResponse response = new HeaderSearchItemResponse(collection.getId(), HeaderSearchEntityType.COLLECTION, title, null, poster, collection.getStartDate() == null ? null : collection.getStartDate().getYear());
            target.add(new RankedItem(response, relevance(query, title), normalize(title)));
        }
    }

    private void addFranchises(List<RankedItem> target, String query) {
        for (Franchise franchise : franchiseRepository.search(query, CatalogEntityStatus.ACTIVE, PageRequest.of(0, RESULT_LIMIT))) {
            HeaderSearchItemResponse response = new HeaderSearchItemResponse(franchise.getId(), HeaderSearchEntityType.FRANCHISE, franchise.getName(), null, franchise.getPosterUrl(), franchise.getStartDate() == null ? null : franchise.getStartDate().getYear());
            target.add(new RankedItem(response, relevance(query, franchise.getName()), normalize(franchise.getName())));
        }
    }

    private void addMedia(
            List<RankedItem> target,
            String query,
            MediaType type,
            String locale,
            UUID viewerId
    ) {
        List<Media> candidates = mediaRepository.findHeaderSearchCandidates(
                query,
                type == null ? null : type.name(),
                PageRequest.of(0, RESULT_LIMIT)
        );
        Map<UUID, MediaCreditService.CreditSummary> credits = mediaCreditService.summaries(candidates);
        Map<UUID, ResolvedMediaTranslation> translations = translationResolver.resolveAll(candidates, locale);
        Map<UUID, UserArtworkResolver.ResolvedArtwork> artworks = viewerId == null
                ? Map.of()
                : userArtworkResolver.resolve(viewerId, candidates);

        for (Media media : candidates) {
            ResolvedMediaTranslation translation = translations.get(media.getId());
            String title = translation == null ? media.getTitle() : translation.title();
            HeaderSearchItemResponse response = new HeaderSearchItemResponse(
                    media.getId(),
                    HeaderSearchEntityType.MEDIA,
                    title,
                    credits.getOrDefault(media.getId(), MediaCreditService.CreditSummary.empty()).creator(),
                    effectiveCover(media, translation, artworks.get(media.getId())),
                    media.getReleaseDate() == null ? null : media.getReleaseDate().getYear()
            );
            target.add(new RankedItem(
                    response,
                    relevance(query, title, media.getTitle(), media.getOriginalTitle()),
                    normalize(title)
            ));
        }
    }

    private String effectiveCover(
            Media media,
            ResolvedMediaTranslation translation,
            UserArtworkResolver.ResolvedArtwork artwork
    ) {
        if (artwork != null && artwork.customCover()) return artwork.coverUrl();
        if (translation != null && translation.coverUrl() != null) return translation.coverUrl();
        return media.getCoverUrl();
    }

    private void addArtists(List<RankedItem> target, String query, MediaType type) {
        List<Person> candidates = personRepository.findHeaderSearchCandidates(
                query,
                type == null ? null : type.name(),
                PageRequest.of(0, RESULT_LIMIT)
        );
        for (Person artist : candidates) {
            HeaderSearchItemResponse response = new HeaderSearchItemResponse(
                    artist.getId(),
                    HeaderSearchEntityType.ARTIST,
                    artist.getName(),
                    null,
                    artist.getImageUrl(),
                    null
            );
            target.add(new RankedItem(
                    response,
                    relevance(query, artist.getName()),
                    normalize(artist.getName())
            ));
        }
    }

    private int relevance(String query, String... values) {
        String normalizedQuery = normalize(query);
        int best = Integer.MAX_VALUE;
        for (String value : values) {
            String normalizedValue = normalize(value);
            if (normalizedValue.equals(normalizedQuery)) {
                best = Math.min(best, 0);
            } else if (normalizedValue.startsWith(normalizedQuery)) {
                best = Math.min(best, 1);
            } else if (normalizedValue.contains(" " + normalizedQuery)) {
                best = Math.min(best, 2);
            } else if (normalizedValue.contains(normalizedQuery)) {
                best = Math.min(best, 3);
            }
        }
        return best;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private record RankedItem(
            HeaderSearchItemResponse response,
            int relevance,
            String normalizedTitle
    ) {
    }
}
