package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.media.dto.response.MediaSearchPageResponse;
import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaSearchSort;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.external.ExternalMedia;
import com.scriptles.cabinet.media.external.ExternalMediaProvider;
import com.scriptles.cabinet.media.external.ExternalMediaProviderRegistry;
import com.scriptles.cabinet.media.repository.ExternalReferenceRepository;
import com.scriptles.cabinet.media.repository.RatingRepository;
import com.scriptles.cabinet.user.enums.Visibility;
import com.scriptles.cabinet.user.repository.UserMediaArtworkPreferenceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaSearchServiceTest {
    @Mock
    private ExternalMediaProviderRegistry providerRegistry;
    @Mock
    private ExternalReferenceRepository externalReferenceRepository;
    @Mock
    private RatingRepository ratingRepository;
    @Mock
    private MediaCreditService mediaCreditService;
    private MediaSearchService mediaSearchService;

    @BeforeEach
    void setUp() {
        UserArtworkResolver artworkResolver = new UserArtworkResolver(
                mock(UserMediaArtworkPreferenceRepository.class));
        mediaSearchService = new MediaSearchService(
                providerRegistry,
                externalReferenceRepository,
                ratingRepository,
                mediaCreditService,
                new MediaSearchItemAssembler(
                        externalReferenceRepository,
                        ratingRepository,
                        mediaCreditService,
                        artworkResolver
                ),
                artworkResolver
        );
    }

    @Test
    void searchesAProviderOnceAndEnrichesImportedMediaInBatches() {
        ExternalMediaProvider tmdb = mock(ExternalMediaProvider.class);
        ExternalMedia result = media(ExternalSource.TMDB, MediaType.MOVIE, "603", "Matrix");
        Media imported = importedMedia(MediaType.MOVIE, "Matrix");
        ExternalReference reference = reference(imported, ExternalSource.TMDB, "603");
        RatingRepository.MediaRatingProjection rating = mock(RatingRepository.MediaRatingProjection.class);

        when(providerRegistry.get(ExternalSource.TMDB, MediaType.MOVIE)).thenReturn(tmdb);
        when(tmdb.search(MediaType.MOVIE, "matrix", "pt-BR", 0, 21)).thenReturn(List.of(result));
        when(externalReferenceRepository.findAllBySourceInAndExternalIdIn(
                Set.of(ExternalSource.TMDB), Set.of("603")))
                .thenReturn(List.of(reference));
        when(rating.getMediaId()).thenReturn(imported.getId());
        when(rating.getAverageRating()).thenReturn(4.5);
        when(rating.getRatingCount()).thenReturn(8L);
        when(ratingRepository.summarizeRatings(List.of(imported.getId()), Visibility.PUBLIC))
                .thenReturn(List.of(rating));
        when(mediaCreditService.summaries(List.of(imported))).thenReturn(Map.of(
                imported.getId(),
                new MediaCreditService.CreditSummary("Lana Wachowski, Lilly Wachowski", null, List.of())
        ));

        MediaSearchPageResponse response = mediaSearchService.search(
                " matrix ", MediaType.MOVIE, MediaSearchSort.RELEVANCE, null, 20);

        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(imported.getId());
            assertThat(item.averageRating()).isEqualTo(4.5);
            assertThat(item.ratingCount()).isEqualTo(8);
            assertThat(item.creator()).isEqualTo("Lana Wachowski, Lilly Wachowski");
        });
        assertThat(response.nextCursor()).isNull();
        verify(tmdb).search(MediaType.MOVIE, "matrix", "pt-BR", 0, 21);
        verify(externalReferenceRepository).findAllBySourceInAndExternalIdIn(
                Set.of(ExternalSource.TMDB), Set.of("603"));
    }

    @Test
    void globalCursorInterleavesProvidersWithoutSkippingOrRepeatingItems() {
        ExternalMediaProvider tmdb = mock(ExternalMediaProvider.class);
        ExternalMediaProvider musicBrainz = mock(ExternalMediaProvider.class);
        ExternalMediaProvider googleBooks = mock(ExternalMediaProvider.class);
        List<ExternalMedia> tmdbResults = List.of(
                media(ExternalSource.TMDB, MediaType.MOVIE, "m1", "Filme 1"),
                media(ExternalSource.TMDB, MediaType.SERIES, "s1", "Série 1"),
                media(ExternalSource.TMDB, MediaType.MOVIE, "m2", "Filme 2")
        );
        List<ExternalMedia> albumResults = List.of(
                media(ExternalSource.MUSICBRAINZ, MediaType.ALBUM, "a1", "Álbum 1"),
                media(ExternalSource.MUSICBRAINZ, MediaType.ALBUM, "a2", "Álbum 2"),
                media(ExternalSource.MUSICBRAINZ, MediaType.ALBUM, "a3", "Álbum 3")
        );
        List<ExternalMedia> bookResults = List.of(
                media(ExternalSource.GOOGLE_BOOKS, MediaType.BOOK, "b1", "Livro 1"),
                media(ExternalSource.GOOGLE_BOOKS, MediaType.BOOK, "b2", "Livro 2")
        );

        when(providerRegistry.get(ExternalSource.TMDB, MediaType.MOVIE)).thenReturn(tmdb);
        when(providerRegistry.get(ExternalSource.MUSICBRAINZ, MediaType.ALBUM)).thenReturn(musicBrainz);
        when(providerRegistry.get(ExternalSource.GOOGLE_BOOKS, MediaType.BOOK)).thenReturn(googleBooks);
        when(tmdb.searchAll(eq("nome"), eq("pt-BR"), anyInt(), eq(3)))
                .thenAnswer(invocation -> slice(tmdbResults, invocation.getArgument(2), 3));
        when(musicBrainz.search(eq(MediaType.ALBUM), eq("nome"), eq("pt-BR"), anyInt(), eq(3)))
                .thenAnswer(invocation -> slice(albumResults, invocation.getArgument(3), 3));
        when(googleBooks.search(eq(MediaType.BOOK), eq("nome"), eq("pt-BR"), anyInt(), eq(3)))
                .thenAnswer(invocation -> slice(bookResults, invocation.getArgument(3), 3));
        when(externalReferenceRepository.findAllBySourceInAndExternalIdIn(anySet(), anySet()))
                .thenReturn(List.of());

        MediaSearchPageResponse first = mediaSearchService.search(
                "nome", null, MediaSearchSort.RELEVANCE, null, 2);
        MediaSearchPageResponse second = mediaSearchService.search(
                "nome", null, MediaSearchSort.RELEVANCE, first.nextCursor(), 2);

        assertThat(first.items()).extracting(item -> item.externalId()).containsExactly("m1", "a1");
        assertThat(second.items()).extracting(item -> item.externalId()).containsExactly("b1", "s1");
        assertThat(first.items()).extracting(item -> item.externalId())
                .doesNotContainAnyElementsOf(second.items().stream().map(item -> item.externalId()).toList());
    }

    @Test
    void searchesBooksWithGoogleBooks() {
        ExternalMediaProvider googleBooks = mock(ExternalMediaProvider.class);
        ExternalMedia result = media(ExternalSource.GOOGLE_BOOKS, MediaType.BOOK, "volume-id", "Duna");

        when(providerRegistry.get(ExternalSource.GOOGLE_BOOKS, MediaType.BOOK)).thenReturn(googleBooks);
        when(googleBooks.search(MediaType.BOOK, "duna", "pt-BR", 0, 21)).thenReturn(List.of(result));
        when(externalReferenceRepository.findAllBySourceInAndExternalIdIn(
                Set.of(ExternalSource.GOOGLE_BOOKS), Set.of("volume-id")))
                .thenReturn(List.of());

        MediaSearchPageResponse response = mediaSearchService.search(
                "duna", MediaType.BOOK, MediaSearchSort.RELEVANCE, null, 20);

        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.type()).isEqualTo(MediaType.BOOK);
            assertThat(item.source()).isEqualTo(ExternalSource.GOOGLE_BOOKS);
        });
    }

    @Test
    void ratingSearchUsesOnlyPublicReviewsAndPrimaryReferences() {
        Media media = importedMedia(MediaType.ALBUM, "Clube da Esquina");
        ExternalReference reference = reference(media, ExternalSource.MUSICBRAINZ, "album-id");
        RatingRepository.RatedMediaProjection projection = mock(RatingRepository.RatedMediaProjection.class);
        when(projection.getMedia()).thenReturn(media);
        when(projection.getAverageRating()).thenReturn(4.75);
        when(projection.getRatingCount()).thenReturn(12L);
        when(ratingRepository.searchRatedMedia(
                eq("clube"),
                eq(Set.of(MediaType.ALBUM.name())),
                eq(Visibility.PUBLIC),
                eq(PageRequest.of(0, 20))))
                .thenReturn(new SliceImpl<>(List.of(projection), PageRequest.of(0, 20), false));
        when(externalReferenceRepository.findAllByMediaIdInAndPrimaryReferenceTrue(List.of(media.getId())))
                .thenReturn(List.of(reference));

        MediaSearchPageResponse response = mediaSearchService.search(
                "clube", MediaType.ALBUM, MediaSearchSort.RATING, null, 20);

        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.externalId()).isEqualTo("album-id");
            assertThat(item.averageRating()).isEqualTo(4.75);
            assertThat(item.ratingCount()).isEqualTo(12);
            assertThat(item.imported()).isTrue();
        });
        verify(providerRegistry, never()).get(eq(ExternalSource.MUSICBRAINZ), eq(MediaType.ALBUM));
    }

    @Test
    void rejectsTypesOutsideTheSearchScreenScope() {
        assertThatThrownBy(() -> mediaSearchService.search(
                "faixa", MediaType.TRACK, MediaSearchSort.RELEVANCE, null, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TRACK");
    }

    private List<ExternalMedia> slice(List<ExternalMedia> values, int offset, int limit) {
        if (offset >= values.size()) {
            return List.of();
        }
        return values.subList(offset, Math.min(offset + limit, values.size()));
    }

    private Media importedMedia(MediaType type, String title) {
        Media media = new Media();
        media.setId(UUID.randomUUID());
        media.setType(type);
        media.setTitle(title);
        return media;
    }

    private ExternalReference reference(
            Media media,
            ExternalSource source,
            String externalId
    ) {
        ExternalReference reference = new ExternalReference();
        reference.setMedia(media);
        reference.setSource(source);
        reference.setExternalId(externalId);
        reference.setPrimaryReference(true);
        return reference;
    }

    private ExternalMedia media(
            ExternalSource source,
            MediaType type,
            String externalId,
            String title
    ) {
        return new ExternalMedia(
                source, externalId, type, title, title, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                List.of(), List.of(), List.of(), List.of()
        );
    }
}
