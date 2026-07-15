package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.lists.repository.MediaListItemRepository;
import com.scriptles.cabinet.media.dto.response.ExternalMediaDetailsResponse;
import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.external.ExternalMedia;
import com.scriptles.cabinet.media.external.ExternalMediaProvider;
import com.scriptles.cabinet.media.external.ExternalMediaProviderRegistry;
import com.scriptles.cabinet.media.external.TmdbClient;
import com.scriptles.cabinet.media.external.WikidataClient;
import com.scriptles.cabinet.media.repository.AlbumDetailsRepository;
import com.scriptles.cabinet.media.repository.AlbumTrackRepository;
import com.scriptles.cabinet.media.repository.BookDetailsRepository;
import com.scriptles.cabinet.media.repository.ExternalReferenceRepository;
import com.scriptles.cabinet.media.repository.MediaLikeRepository;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.media.repository.MovieDetailsRepository;
import com.scriptles.cabinet.media.repository.ReviewRepository;
import com.scriptles.cabinet.media.repository.SeriesDetailsRepository;
import com.scriptles.cabinet.media.repository.SeriesSeasonRepository;
import com.scriptles.cabinet.media.repository.TrackDetailsRepository;
import com.scriptles.cabinet.user.enums.UserMediaStatus;
import com.scriptles.cabinet.user.enums.Visibility;
import com.scriptles.cabinet.user.repository.UserMediaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExternalMediaServiceTest {
    @Mock
    private ExternalMediaProviderRegistry providerRegistry;
    @Mock
    private MediaRepository mediaRepository;
    @Mock
    private ExternalReferenceRepository externalReferenceRepository;
    @Mock
    private AlbumDetailsRepository albumDetailsRepository;
    @Mock
    private BookDetailsRepository bookDetailsRepository;
    @Mock
    private MovieDetailsRepository movieDetailsRepository;
    @Mock
    private SeriesDetailsRepository seriesDetailsRepository;
    @Mock
    private AlbumTrackRepository albumTrackRepository;
    @Mock
    private SeriesSeasonRepository seriesSeasonRepository;
    @Mock
    private TrackDetailsRepository trackDetailsRepository;
    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private MediaLikeRepository mediaLikeRepository;
    @Mock
    private MediaListItemRepository mediaListItemRepository;
    @Mock
    private UserMediaRepository userMediaRepository;
    @Mock
    private WikidataClient wikidataClient;
    @Mock
    private TmdbClient tmdbClient;
    @Mock
    private MediaQueryService mediaQueryService;
    @Mock
    private ExternalMediaProvider provider;

    @InjectMocks
    private ExternalMediaService externalMediaService;

    @Test
    void includesPublicCommunityStatsForImportedMedia() {
        UUID mediaId = UUID.randomUUID();
        Media media = new Media();
        media.setId(mediaId);
        ExternalReference reference = new ExternalReference();
        reference.setMedia(media);

        ReviewRepository.MediaRatingProjection rating = mock(ReviewRepository.MediaRatingProjection.class);
        ReviewRepository.RatingDistributionProjection fiveStars =
                mock(ReviewRepository.RatingDistributionProjection.class);
        ReviewRepository.RatingDistributionProjection fourStars =
                mock(ReviewRepository.RatingDistributionProjection.class);
        when(rating.getAverageRating()).thenReturn(4.25);
        when(fiveStars.getRating()).thenReturn(new BigDecimal("5.0"));
        when(fiveStars.getRatingCount()).thenReturn(3L);
        when(fourStars.getRating()).thenReturn(new BigDecimal("4.0"));
        when(fourStars.getRatingCount()).thenReturn(1L);
        when(providerRegistry.get(ExternalSource.TMDB, MediaType.MOVIE)).thenReturn(provider);
        when(provider.findById(MediaType.MOVIE, "550", "pt-BR"))
                .thenReturn(Optional.of(movie()));
        when(wikidataClient.find(ExternalSource.TMDB, MediaType.MOVIE, "550", "pt-BR"))
                .thenReturn(Optional.empty());
        when(externalReferenceRepository.findBySourceAndExternalId(ExternalSource.TMDB, "550"))
                .thenReturn(Optional.empty(), Optional.of(reference));
        when(reviewRepository.summarizeRatings(List.of(mediaId), Visibility.PUBLIC))
                .thenReturn(List.of(rating));
        when(reviewRepository.ratingDistribution(mediaId, Visibility.PUBLIC))
                .thenReturn(List.of(fourStars, fiveStars));
        when(mediaLikeRepository.countByMediaId(mediaId)).thenReturn(12L);
        when(mediaListItemRepository.countByMediaIdAndListVisibility(mediaId, Visibility.PUBLIC))
                .thenReturn(7L);
        when(userMediaRepository.countByMediaIdAndStatusAndPrivateEntryFalse(
                mediaId, UserMediaStatus.COMPLETED)).thenReturn(31L);

        var response = externalMediaService.findDetails(
                ExternalSource.TMDB,
                MediaType.MOVIE,
                "550",
                "pt-BR"
        );

        assertThat(response.likeCount()).isEqualTo(12);
        assertThat(response.averageRating()).isEqualTo(4.25);
        assertThat(response.ratingDistribution())
                .extracting(
                        ExternalMediaDetailsResponse.RatingDistributionBucket::rating,
                        ExternalMediaDetailsResponse.RatingDistributionBucket::count
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(0.5, 0L),
                        org.assertj.core.groups.Tuple.tuple(1.0, 0L),
                        org.assertj.core.groups.Tuple.tuple(1.5, 0L),
                        org.assertj.core.groups.Tuple.tuple(2.0, 0L),
                        org.assertj.core.groups.Tuple.tuple(2.5, 0L),
                        org.assertj.core.groups.Tuple.tuple(3.0, 0L),
                        org.assertj.core.groups.Tuple.tuple(3.5, 0L),
                        org.assertj.core.groups.Tuple.tuple(4.0, 1L),
                        org.assertj.core.groups.Tuple.tuple(4.5, 0L),
                        org.assertj.core.groups.Tuple.tuple(5.0, 3L)
                );
        assertThat(response.listCount()).isEqualTo(7);
        assertThat(response.completedCount()).isEqualTo(31);
    }

    @Test
    void usesStoredMediaWithoutCallingTheExternalProvider() {
        UUID mediaId = UUID.randomUUID();
        Media media = new Media();
        media.setId(mediaId);
        ExternalReference reference = new ExternalReference();
        reference.setMedia(media);
        ExternalMediaDetailsResponse stored = new ExternalMediaDetailsResponse(
                mediaId,
                "550",
                ExternalSource.TMDB,
                MediaType.MOVIE,
                "Fight Club",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Map.of(),
                List.of(),
                true,
                0,
                null,
                List.of(),
                0,
                0,
                new ExternalMediaDetailsResponse.MovieDetails(null, null, null, null)
        );

        when(externalReferenceRepository.findBySourceAndExternalId(ExternalSource.TMDB, "550"))
                .thenReturn(Optional.of(reference));
        when(mediaQueryService.findDetails(mediaId)).thenReturn(stored);

        ExternalMediaDetailsResponse response = externalMediaService.findDetails(
                ExternalSource.TMDB,
                MediaType.MOVIE,
                "550",
                "pt-BR"
        );

        assertThat(response).isSameAs(stored);
        verify(mediaQueryService).findDetails(mediaId);
        verifyNoInteractions(providerRegistry);
    }

    private ExternalMedia movie() {
        return new ExternalMedia(
                ExternalSource.TMDB, "550", MediaType.MOVIE, "Fight Club", "Fight Club", null, null,
                null, null, null, null, "en", "US", null, null, null, null, null, null, 139, null, null,
                null, null, null, null, null, null, "David Fincher", null, null,
                List.of(), List.of(), List.of()
        );
    }
}
