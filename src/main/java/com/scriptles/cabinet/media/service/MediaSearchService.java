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
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.media.repository.MediaSearchDocumentRepository;
import com.scriptles.cabinet.media.repository.RatingRepository;
import com.scriptles.cabinet.media.translation.CatalogLocaleResolver;
import com.scriptles.cabinet.media.translation.MediaTranslationResolver;
import com.scriptles.cabinet.media.translation.ResolvedMediaTranslation;
import com.scriptles.cabinet.user.enums.Visibility;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.function.Supplier;

@Service
public class MediaSearchService {
    private static final Set<MediaType> SEARCHABLE_TYPES = EnumSet.of(
            MediaType.MOVIE,
            MediaType.SERIES,
            MediaType.ALBUM,
            MediaType.BOOK
    );

    private final ExternalMediaProviderRegistry providerRegistry;
    private final ExternalReferenceRepository externalReferenceRepository;
    private final MediaRepository mediaRepository;
    private final MediaSearchDocumentRepository searchDocumentRepository;
    private final RatingRepository ratingRepository;
    private final MediaCreditService mediaCreditService;
    private final MediaSearchItemAssembler mediaSearchItemAssembler;
    private final UserArtworkResolver userArtworkResolver;
    private final CatalogLocaleResolver localeResolver;
    private final MediaTranslationResolver translationResolver;
    private final MeterRegistry meterRegistry;
    private final SearchOperationalState searchOperationalState;

    @Autowired
    public MediaSearchService(
            ExternalMediaProviderRegistry providerRegistry,
            ExternalReferenceRepository externalReferenceRepository,
            MediaRepository mediaRepository,
            MediaSearchDocumentRepository searchDocumentRepository,
            RatingRepository ratingRepository,
            MediaCreditService mediaCreditService,
            MediaSearchItemAssembler mediaSearchItemAssembler,
            UserArtworkResolver userArtworkResolver,
            CatalogLocaleResolver localeResolver,
            MediaTranslationResolver translationResolver,
            MeterRegistry meterRegistry,
            SearchOperationalState searchOperationalState
    ) {
        this.providerRegistry = providerRegistry;
        this.externalReferenceRepository = externalReferenceRepository;
        this.mediaRepository = mediaRepository;
        this.searchDocumentRepository = searchDocumentRepository;
        this.ratingRepository = ratingRepository;
        this.mediaCreditService = mediaCreditService;
        this.mediaSearchItemAssembler = mediaSearchItemAssembler;
        this.userArtworkResolver = userArtworkResolver;
        this.localeResolver = localeResolver;
        this.translationResolver = translationResolver;
        this.meterRegistry = meterRegistry;
        this.searchOperationalState = searchOperationalState;
    }

    public MediaSearchService(
            ExternalMediaProviderRegistry providerRegistry,
            ExternalReferenceRepository externalReferenceRepository,
            MediaRepository mediaRepository,
            MediaSearchDocumentRepository searchDocumentRepository,
            RatingRepository ratingRepository,
            MediaCreditService mediaCreditService,
            MediaSearchItemAssembler mediaSearchItemAssembler,
            UserArtworkResolver userArtworkResolver,
            CatalogLocaleResolver localeResolver,
            MediaTranslationResolver translationResolver,
            MeterRegistry meterRegistry
    ) {
        this(providerRegistry, externalReferenceRepository, mediaRepository, searchDocumentRepository, ratingRepository,
                mediaCreditService, mediaSearchItemAssembler, userArtworkResolver, localeResolver, translationResolver,
                meterRegistry, new SearchOperationalState());
    }

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

        try {
            MediaSearchPageResponse result = timed("api", () -> sort == MediaSearchSort.RATING
                    ? timed("rating", () -> searchByRating(normalizedQuery, type, cursor, limit, viewerId, requestedLocale))
                    : searchByRelevance(normalizedQuery, type, cursor, limit, viewerId, requestedLocale));
            if (result.items().isEmpty()) meterRegistry.counter("cabinet.search.zero_results").increment();
            searchOperationalState.recordSuccess();
            return result;
        } catch (RuntimeException failure) {
            meterRegistry.counter("cabinet.search.failure").increment();
            searchOperationalState.recordFailure();
            throw failure;
        }
    }

    private MediaSearchPageResponse searchByRelevance(
            String query,
            MediaType type,
            String cursor,
            int limit,
            UUID viewerId,
            String locale
    ) {
        LocalFirstCursor state = decodeLocalFirstCursor(cursor);
        if (state.providersStarted()) {
            meterRegistry.counter("cabinet.search.provider.fallback").increment();
            MediaSearchPageResponse external = searchExternalByRelevance(
                    query, type, state.providerCursor(), limit, viewerId, locale);
            return new MediaSearchPageResponse(
                    deduplicate(external.items(), Set.of(), limit),
                    external.nextCursor() == null ? null : encodeLocalFirstCursor(
                            state.localOffset(), true, external.nextCursor()));
        }

        List<UUID> localIds = timed("local", () -> searchDocumentRepository.search(
                normalizeForSearch(query),
                type == null ? null : type.name(),
                localeResolver.fallbackChain(locale),
                limit + 1,
                state.localOffset()
        ));
        List<UUID> selectedLocalIds = localIds.stream().limit(limit).toList();
        meterRegistry.summary("cabinet.search.local.results").record(selectedLocalIds.size());
        List<Media> loadedMedia = mediaRepository.findAllById(selectedLocalIds);
        Map<UUID, Media> mediaById = loadedMedia.stream().collect(Collectors.toMap(
                Media::getId, media -> media, (first, ignored) -> first));
        List<Media> orderedMedia = selectedLocalIds.stream()
                .map(mediaById::get)
                .filter(java.util.Objects::nonNull)
                .toList();
        List<MediaSearchItemResponse> localItems = timed("enrichment", () ->
                mediaSearchItemAssembler.fromImported(orderedMedia, viewerId, locale));

        if (localIds.size() > limit) {
            return new MediaSearchPageResponse(localItems,
                    encodeLocalFirstCursor(state.localOffset() + selectedLocalIds.size(), false, null));
        }
        if (localIds.size() == limit) {
            return new MediaSearchPageResponse(localItems, null);
        }

        int remaining = limit - localItems.size();
        if (remaining <= 0) return new MediaSearchPageResponse(localItems, null);

        meterRegistry.counter("cabinet.search.provider.fallback").increment();
        MediaSearchPageResponse external = searchExternalByRelevance(query, type, null, remaining, viewerId, locale);
        Set<SearchIdentity> localIdentities = localItems.stream()
                .map(MediaSearchService::identity)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        List<MediaSearchItemResponse> merged = new ArrayList<>(localItems);
        merged.addAll(deduplicate(external.items(), localIdentities, remaining));
        String nextCursor = external.nextCursor() == null
                ? null
                : encodeLocalFirstCursor(state.localOffset() + selectedLocalIds.size(), true, external.nextCursor());
        return new MediaSearchPageResponse(merged, nextCursor);
    }

    private MediaSearchPageResponse searchExternalByRelevance(
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
        List<ExternalMedia> fetched = timed(defaultSource(type).name().toLowerCase(java.util.Locale.ROOT),
                () -> provider.search(type, query, locale, state.offset(), limit + 1));
        List<ExternalMedia> page = fetched.stream().limit(limit).toList();
        int consumed = page.size();
        String nextCursor = fetched.size() > consumed
                ? encode("s:%d".formatted(state.offset() + consumed))
                : null;

        List<MediaSearchItemResponse> items = timed("enrichment",
                () -> mediaSearchItemAssembler.fromExternal(page, viewerId));
        return new MediaSearchPageResponse(deduplicate(items, Set.of(), limit), nextCursor);
    }

    private String normalizeForSearch(String query) {
        return query.toLowerCase(java.util.Locale.ROOT).trim();
    }

    private List<MediaSearchItemResponse> deduplicate(
            List<MediaSearchItemResponse> items,
            Set<SearchIdentity> existing,
            int limit
    ) {
        Set<SearchIdentity> seen = new java.util.HashSet<>(existing);
        return items.stream()
                .filter(item -> identity(item) == null || seen.add(identity(item)))
                .limit(limit)
                .toList();
    }

    private static SearchIdentity identity(MediaSearchItemResponse item) {
        if (item == null || item.source() == null || item.type() == null || item.externalId() == null) {
            return null;
        }
        return new SearchIdentity(item.source(), item.type(), item.externalId());
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

        List<ExternalMedia> tmdb;
        List<ExternalMedia> albums;
        List<ExternalMedia> books;
        boolean albumFailed;
        boolean bookFailed;
        // Provider calls do not use the JPA session. Run them together so the response
        // waits for the slowest catalog instead of the sum of all three latencies.
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            CompletableFuture<List<ExternalMedia>> tmdbFuture = CompletableFuture.supplyAsync(
                    () -> state.tmdbDone() ? List.of() : timed("tmdb", () -> providerRegistry
                            .get(ExternalSource.TMDB, MediaType.MOVIE)
                            .searchAll(query, locale, state.tmdbOffset(), fetchLimit)), executor);
            CompletableFuture<ProviderResult> albumFuture = CompletableFuture.supplyAsync(
                    () -> searchOptionalProvider(state.albumDone(), () -> timed("musicbrainz", () -> providerRegistry
                            .get(ExternalSource.MUSICBRAINZ, MediaType.ALBUM)
                            .search(MediaType.ALBUM, query, locale, state.albumOffset(), fetchLimit))), executor);
            CompletableFuture<ProviderResult> bookFuture = CompletableFuture.supplyAsync(
                    () -> searchOptionalProvider(state.bookDone(), () -> timed("google_books", () -> providerRegistry
                            .get(ExternalSource.GOOGLE_BOOKS, MediaType.BOOK)
                            .search(MediaType.BOOK, query, locale, state.bookOffset(), fetchLimit))), executor);

            try {
                tmdb = tmdbFuture.join();
            } catch (CompletionException exception) {
                if (exception.getCause() instanceof RuntimeException cause) throw cause;
                throw exception;
            }
            ProviderResult albumResult = albumFuture.join();
            ProviderResult bookResult = bookFuture.join();
            albums = albumResult.items();
            books = bookResult.items();
            albumFailed = albumResult.failed();
            bookFailed = bookResult.failed();
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
                timed("enrichment", () -> mediaSearchItemAssembler.fromExternal(interleaved.items(), viewerId)), nextCursor);
    }

    private <T> T timed(String stage, Supplier<T> action) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            return action.get();
        } finally {
            sample.stop(Timer.builder("cabinet.search.duration").tag("stage", stage).register(meterRegistry));
        }
    }

    private ProviderResult searchOptionalProvider(boolean done, java.util.function.Supplier<List<ExternalMedia>> search) {
        if (done) return new ProviderResult(List.of(), false);
        try {
            return new ProviderResult(search.get(), false);
        } catch (ExternalMediaException exception) {
            return new ProviderResult(List.of(), true);
        }
    }

    private record ProviderResult(List<ExternalMedia> items, boolean failed) {}

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
        meterRegistry.summary("cabinet.search.local.results").record(items.size());
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

    private LocalFirstCursor decodeLocalFirstCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return new LocalFirstCursor(0, false, null);
        }
        String decoded = decode(cursor);
        // Accept provider cursors issued before local-first search was enabled.
        if (decoded.startsWith("s:") || decoded.startsWith("a:")) {
            return new LocalFirstCursor(0, true, cursor);
        }
        String[] values = decoded.split(":", 4);
        if (values.length != 4 || !"l".equals(values[0])) throw invalidCursor();
        try {
            int localOffset = nonNegative(values[1]);
            boolean providersStarted = switch (values[2]) {
                case "local" -> false;
                case "external" -> true;
                default -> throw invalidCursor();
            };
            String providerCursor = "-".equals(values[3]) ? null : values[3];
            if (providersStarted && providerCursor == null) throw invalidCursor();
            if (!providersStarted && providerCursor != null) throw invalidCursor();
            return new LocalFirstCursor(localOffset, providersStarted, providerCursor);
        } catch (RuntimeException exception) {
            throw invalidCursor();
        }
    }

    private String encodeLocalFirstCursor(int localOffset, boolean providersStarted, String providerCursor) {
        return encode("l:%d:%s:%s".formatted(
                localOffset,
                providersStarted ? "external" : "local",
                providerCursor == null ? "-" : providerCursor
        ));
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

    private record LocalFirstCursor(int localOffset, boolean providersStarted, String providerCursor) {
    }

    private record SearchIdentity(ExternalSource source, MediaType type, String externalId) {
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
