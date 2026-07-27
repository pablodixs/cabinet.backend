package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.media.dto.response.MediaSearchItemResponse;
import com.scriptles.cabinet.media.dto.response.MediaSearchPageResponse;
import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaSearchSort;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.external.ExternalMedia;
import com.scriptles.cabinet.media.external.ExternalMediaException;
import com.scriptles.cabinet.media.external.ExternalMediaProvider;
import com.scriptles.cabinet.media.external.ExternalMediaProviderRegistry;
import com.scriptles.cabinet.media.repository.ExternalReferenceRepository;
import com.scriptles.cabinet.media.repository.RatingRepository;
import com.scriptles.cabinet.media.translation.CatalogLocaleResolver;
import com.scriptles.cabinet.media.translation.MediaTranslationResolver;
import com.scriptles.cabinet.media.translation.ResolvedMediaTranslation;
import com.scriptles.cabinet.user.enums.Visibility;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MediaSearchService {
    private static final Set<MediaType> SEARCHABLE_TYPES = EnumSet.of(
            MediaType.MOVIE,
            MediaType.SERIES,
            MediaType.ALBUM,
            MediaType.BOOK
    );

    private final ExternalMediaProviderRegistry providerRegistry;
    private final ExternalReferenceRepository externalReferenceRepository;
    private final RatingRepository ratingRepository;
    private final MediaCreditService mediaCreditService;
    private final MediaSearchItemAssembler mediaSearchItemAssembler;
    private final UserArtworkResolver userArtworkResolver;
    private final CatalogLocaleResolver localeResolver;
    private final MediaTranslationResolver translationResolver;

    @Transactional(readOnly = true)
    public MediaSearchPageResponse search(
            String query,
            MediaType type,
            MediaSearchSort sort,
            String cursor,
            int limit
    ) {
        return search(query, type, sort, cursor, limit, null, CatalogLocaleResolver.DEFAULT_LOCALE);
    }

    @Transactional(readOnly = true)
    public MediaSearchPageResponse search(
            String query,
            MediaType type,
            MediaSearchSort sort,
            String cursor,
            int limit,
            UUID viewerId
    ) {
        return search(query, type, sort, cursor, limit, viewerId, CatalogLocaleResolver.DEFAULT_LOCALE);
    }

    @Transactional(readOnly = true)
    public MediaSearchPageResponse search(
            String query,
            MediaType type,
            MediaSearchSort sort,
            String cursor,
            int limit,
            String locale
    ) {
        return search(query, type, sort, cursor, limit, null, locale);
    }

    @Transactional(readOnly = true)
    public MediaSearchPageResponse search(
            String query,
            MediaType type,
            MediaSearchSort sort,
            String cursor,
            int limit,
            UUID viewerId,
            String locale
    ) {
        validateType(type);
        String normalizedQuery = query.trim();
        String requestedLocale = localeResolver.normalize(locale);

        if (sort == MediaSearchSort.RATING) {
            return searchByRating(normalizedQuery, type, cursor, limit, viewerId, requestedLocale);
        }
        return searchByRelevance(normalizedQuery, type, cursor, limit, viewerId, requestedLocale);
    }

    private MediaSearchPageResponse searchByRelevance(
            String query,
            MediaType type,
            String cursor,
            int limit,
            UUID viewerId,
            String locale
    ) {
        if (type == null) {
            return searchAllByRelevance(query, cursor, limit, viewerId, locale);
        }

        SingleCursor state = decodeSingleCursor(cursor);
        ExternalMediaProvider provider = providerRegistry.get(defaultSource(type), type);
        List<ExternalMedia> fetched = provider.search(type, query, locale, state.offset(), limit + 1);
        List<ExternalMedia> page = fetched.stream().limit(limit).toList();
        int consumed = page.size();
        String nextCursor = fetched.size() > consumed
                ? encode("s:%d".formatted(state.offset() + consumed))
                : null;

        return new MediaSearchPageResponse(mediaSearchItemAssembler.fromExternal(page, viewerId), nextCursor);
    }

    private MediaSearchPageResponse searchAllByRelevance(
            String query,
            String cursor,
            int limit,
            UUID viewerId,
            String locale
    ) {
        AllCursor state = decodeAllCursor(cursor);
        int fetchLimit = limit + 1;

        List<ExternalMedia> tmdb = state.tmdbDone()
                ? List.of()
                : providerRegistry.get(ExternalSource.TMDB, MediaType.MOVIE)
                .searchAll(query, locale, state.tmdbOffset(), fetchLimit);

        List<ExternalMedia> albums;
        boolean albumFailed = false;
        try {
            albums = state.albumDone()
                    ? List.of()
                    : providerRegistry.get(ExternalSource.MUSICBRAINZ, MediaType.ALBUM)
                    .search(MediaType.ALBUM, query, locale, state.albumOffset(), fetchLimit);
        } catch (ExternalMediaException exception) {
            albums = List.of();
            albumFailed = true;
        }

        List<ExternalMedia> books;
        boolean bookFailed = false;
        try {
            books = state.bookDone()
                    ? List.of()
                    : providerRegistry.get(ExternalSource.GOOGLE_BOOKS, MediaType.BOOK)
                    .search(MediaType.BOOK, query, locale, state.bookOffset(), fetchLimit);
        } catch (ExternalMediaException exception) {
            books = List.of();
            bookFailed = true;
        }

        InterleavedPage interleaved = interleave(tmdb, albums, books, state.nextSource(), limit);
        int tmdbOffset = state.tmdbOffset() + interleaved.tmdbConsumed();
        int albumOffset = state.albumOffset() + interleaved.albumConsumed();
        int bookOffset = state.bookOffset() + interleaved.bookConsumed();
        boolean tmdbDone = state.tmdbDone()
                || (tmdb.size() < fetchLimit && interleaved.tmdbConsumed() == tmdb.size());
        boolean albumDone = state.albumDone()
                || albumFailed
                || (albums.size() < fetchLimit && interleaved.albumConsumed() == albums.size());
        boolean bookDone = state.bookDone()
                || bookFailed
                || (books.size() < fetchLimit && interleaved.bookConsumed() == books.size());

        String nextCursor = tmdbDone && albumDone && bookDone
                ? null
                : encode("a:%d:%d:%d:%s:%s:%s:%s".formatted(
                tmdbOffset,
                albumOffset,
                bookOffset,
                tmdbDone,
                albumDone,
                bookDone,
                interleaved.nextSource().name()
        ));

        return new MediaSearchPageResponse(
                mediaSearchItemAssembler.fromExternal(interleaved.items(), viewerId), nextCursor);
    }

    private MediaSearchPageResponse searchByRating(
            String query,
            MediaType type,
            String cursor,
            int limit,
            UUID viewerId,
            String locale
    ) {
        RatingCursor state = decodeRatingCursor(cursor);
        Set<MediaType> types = type == null ? SEARCHABLE_TYPES : EnumSet.of(type);
        Slice<RatingRepository.RatedMediaProjection> ratings = ratingRepository.searchRatedMedia(
                query,
                types.stream().map(Enum::name).collect(Collectors.toSet()),
                Visibility.PUBLIC,
                PageRequest.of(state.page(), limit)
        );

        List<UUID> mediaIds = ratings.getContent().stream()
                .map(projection -> projection.getMedia().getId())
                .toList();
        Map<UUID, ExternalReference> references = externalReferenceRepository
                .findAllByMediaIdInAndPrimaryReferenceTrue(mediaIds)
                .stream()
                .collect(Collectors.toMap(
                        reference -> reference.getMedia().getId(),
                        reference -> reference,
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));
        Map<UUID, MediaCreditService.CreditSummary> creditSummaries = mediaCreditService.summaries(
                ratings.getContent().stream().map(RatingRepository.RatedMediaProjection::getMedia).toList()
        );
        Map<UUID, UserArtworkResolver.ResolvedArtwork> artworks = userArtworkResolver.resolve(
                viewerId,
                ratings.getContent().stream().map(RatingRepository.RatedMediaProjection::getMedia).toList()
        );
        Map<UUID, ResolvedMediaTranslation> translations = translationResolver.resolveAll(
                ratings.getContent().stream().map(RatingRepository.RatedMediaProjection::getMedia).toList(),
                locale
        );

        List<MediaSearchItemResponse> items = ratings.getContent().stream()
                .map(projection -> toRatedResponse(
                        projection,
                        references.get(projection.getMedia().getId()),
                        creditSummaries.getOrDefault(
                                projection.getMedia().getId(), MediaCreditService.CreditSummary.empty()),
                        artworks.get(projection.getMedia().getId()),
                        translations.get(projection.getMedia().getId())
                ))
                .filter(java.util.Objects::nonNull)
                .toList();
        String nextCursor = ratings.hasNext()
                ? encode("r:%d".formatted(state.page() + 1))
                : null;

        return new MediaSearchPageResponse(items, nextCursor);
    }

    private MediaSearchItemResponse toRatedResponse(
            RatingRepository.RatedMediaProjection projection,
            ExternalReference reference,
            MediaCreditService.CreditSummary creditSummary,
            UserArtworkResolver.ResolvedArtwork artwork,
            ResolvedMediaTranslation translation
    ) {
        if (reference == null) {
            return null;
        }
        Media media = projection.getMedia();
        return new MediaSearchItemResponse(
                media.getId(),
                reference.getExternalId(),
                reference.getSource(),
                media.getType(),
                translation == null ? media.getTitle() : translation.title(),
                creditSummary.creator(),
                translation == null ? media.getDescription() : translation.description(),
                artwork.customCover() || translation == null
                        ? artwork.coverUrl()
                        : translation.coverUrl(),
                media.getReleaseDate(),
                true,
                projection.getAverageRating(),
                projection.getRatingCount()
        );
    }

    private InterleavedPage interleave(
            List<ExternalMedia> tmdb,
            List<ExternalMedia> albums,
            List<ExternalMedia> books,
            SourceSlot startingSource,
            int limit
    ) {
        List<ExternalMedia> items = new ArrayList<>(limit);
        int tmdbIndex = 0;
        int albumIndex = 0;
        int bookIndex = 0;
        SourceSlot next = startingSource;

        while (items.size() < limit
                && (tmdbIndex < tmdb.size() || albumIndex < albums.size() || bookIndex < books.size())) {
            boolean added = false;
            for (int attempt = 0; attempt < SourceSlot.values().length && !added; attempt++) {
                switch (next) {
                    case TMDB -> {
                        if (tmdbIndex < tmdb.size()) {
                            items.add(tmdb.get(tmdbIndex++));
                            added = true;
                        }
                    }
                    case ALBUM -> {
                        if (albumIndex < albums.size()) {
                            items.add(albums.get(albumIndex++));
                            added = true;
                        }
                    }
                    case BOOK -> {
                        if (bookIndex < books.size()) {
                            items.add(books.get(bookIndex++));
                            added = true;
                        }
                    }
                }
                next = next.next();
            }
        }

        return new InterleavedPage(items, tmdbIndex, albumIndex, bookIndex, next);
    }

    private ExternalSource defaultSource(MediaType type) {
        return switch (type) {
            case ALBUM -> ExternalSource.MUSICBRAINZ;
            case BOOK -> ExternalSource.GOOGLE_BOOKS;
            case MOVIE, SERIES -> ExternalSource.TMDB;
            default -> throw new IllegalArgumentException("Search is unavailable for type " + type);
        };
    }

    private void validateType(MediaType type) {
        if (type != null && !SEARCHABLE_TYPES.contains(type)) {
            throw new IllegalArgumentException("Search is unavailable for type " + type);
        }
    }

    private SingleCursor decodeSingleCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return new SingleCursor(0);
        }
        String[] values = decode(cursor).split(":");
        if (values.length != 2 || !"s".equals(values[0])) {
            throw invalidCursor();
        }
        try {
            return new SingleCursor(nonNegative(values[1]));
        } catch (RuntimeException exception) {
            throw invalidCursor();
        }
    }

    private RatingCursor decodeRatingCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return new RatingCursor(0);
        }
        String[] values = decode(cursor).split(":");
        if (values.length != 2 || !"r".equals(values[0])) {
            throw invalidCursor();
        }
        try {
            return new RatingCursor(nonNegative(values[1]));
        } catch (RuntimeException exception) {
            throw invalidCursor();
        }
    }

    private AllCursor decodeAllCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return new AllCursor(0, 0, 0, false, false, false, SourceSlot.TMDB);
        }
        String[] values = decode(cursor).split(":");
        if (values.length != 8 || !"a".equals(values[0])) {
            throw invalidCursor();
        }
        try {
            return new AllCursor(
                    nonNegative(values[1]),
                    nonNegative(values[2]),
                    nonNegative(values[3]),
                    Boolean.parseBoolean(values[4]),
                    Boolean.parseBoolean(values[5]),
                    Boolean.parseBoolean(values[6]),
                    SourceSlot.valueOf(values[7])
            );
        } catch (RuntimeException exception) {
            throw invalidCursor();
        }
    }

    private int nonNegative(String value) {
        int parsed = Integer.parseInt(value);
        if (parsed < 0) {
            throw invalidCursor();
        }
        return parsed;
    }

    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String decode(String value) {
        try {
            return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw invalidCursor();
        }
    }

    private IllegalArgumentException invalidCursor() {
        return new IllegalArgumentException("Invalid media search cursor");
    }

    private enum SourceSlot {
        TMDB,
        ALBUM,
        BOOK;

        private SourceSlot next() {
            SourceSlot[] values = SourceSlot.values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    private record SingleCursor(int offset) {
    }

    private record RatingCursor(int page) {
    }

    private record AllCursor(
            int tmdbOffset,
            int albumOffset,
            int bookOffset,
            boolean tmdbDone,
            boolean albumDone,
            boolean bookDone,
            SourceSlot nextSource
    ) {
    }

    private record InterleavedPage(
            List<ExternalMedia> items,
            int tmdbConsumed,
            int albumConsumed,
            int bookConsumed,
            SourceSlot nextSource
    ) {
    }
}
