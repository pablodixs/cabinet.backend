package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.lists.repository.MediaListItemRepository;
import com.scriptles.cabinet.media.dto.response.ExternalMediaDetailsResponse;
import com.scriptles.cabinet.media.dto.request.ImportExternalMediaRequest;
import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.MediaLike;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.CreditRole;
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
import com.scriptles.cabinet.media.repository.RatingRepository;
import com.scriptles.cabinet.media.repository.SeriesEpisodeRepository;
import com.scriptles.cabinet.media.repository.SeriesDetailsRepository;
import com.scriptles.cabinet.media.repository.SeriesSeasonRepository;
import com.scriptles.cabinet.media.repository.TrackDetailsRepository;
import com.scriptles.cabinet.user.enums.UserMediaStatus;
import com.scriptles.cabinet.user.enums.Visibility;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.entity.UserMedia;
import com.scriptles.cabinet.user.repository.UserMediaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.atLeastOnce;
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
    private RatingRepository ratingRepository;
    @Mock
    private SeriesEpisodeRepository seriesEpisodeRepository;
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
    private MediaCreditService mediaCreditService;
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
        User liker = communityUser("ana", "https://example.com/ana.jpg");
        MediaLike mediaLike = new MediaLike();
        mediaLike.setUser(liker);
        User completer = communityUser("bia", "https://example.com/bia.jpg");
        UserMedia completed = new UserMedia();
        completed.setUser(completer);

        RatingRepository.MediaRatingProjection rating = mock(RatingRepository.MediaRatingProjection.class);
        RatingRepository.RatingDistributionProjection fiveStars =
                mock(RatingRepository.RatingDistributionProjection.class);
        RatingRepository.RatingDistributionProjection fourStars =
                mock(RatingRepository.RatingDistributionProjection.class);
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
        when(ratingRepository.summarizeRatings(List.of(mediaId), Visibility.PUBLIC))
                .thenReturn(List.of(rating));
        when(ratingRepository.ratingDistribution(mediaId, Visibility.PUBLIC))
                .thenReturn(List.of(fourStars, fiveStars));
        when(mediaLikeRepository.countByMediaId(mediaId)).thenReturn(12L);
        when(mediaLikeRepository.findTop3ByMediaIdOrderByLikedAtDescIdDesc(mediaId))
                .thenReturn(List.of(mediaLike));
        when(mediaListItemRepository.countByMediaIdAndListVisibility(mediaId, Visibility.PUBLIC))
                .thenReturn(7L);
        when(userMediaRepository.countByMediaIdAndStatusAndPrivateEntryFalse(
                mediaId, UserMediaStatus.COMPLETED)).thenReturn(31L);
        when(userMediaRepository
                .findTop3ByMediaIdAndStatusAndPrivateEntryFalseAndCompletedAtIsNotNullOrderByCompletedAtDescIdDesc(
                        mediaId, UserMediaStatus.COMPLETED))
                .thenReturn(List.of(completed));

        var response = externalMediaService.findDetails(
                ExternalSource.TMDB,
                MediaType.MOVIE,
                "550",
                "pt-BR"
        );

        assertThat(response.likeCount()).isEqualTo(12);
        assertThat(response.recentLikers()).singleElement().satisfies(user -> {
            assertThat(user.username()).isEqualTo("ana");
            assertThat(user.avatarUrl()).isEqualTo("https://example.com/ana.jpg");
        });
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
        assertThat(response.recentCompleters()).singleElement().satisfies(user -> {
            assertThat(user.username()).isEqualTo("bia");
            assertThat(user.avatarUrl()).isEqualTo("https://example.com/bia.jpg");
        });
        assertThat(response.credits()).singleElement().satisfies(credit -> {
            assertThat(credit.personId()).isNull();
            assertThat(credit.name()).isEqualTo("David Fincher");
            assertThat(credit.role()).isEqualTo(CreditRole.DIRECTOR);
        });
    }

    private User communityUser(String username, String avatarUrl) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername(username);
        user.setAvatarUlr(avatarUrl);
        return user;
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
                List.of(),
                true,
                0,
                List.of(),
                null,
                List.of(),
                0,
                0,
                List.of(),
                new ExternalMediaDetailsResponse.MovieDetails(null, null, null, null)
        );

        when(externalReferenceRepository.findBySourceAndExternalId(ExternalSource.TMDB, "550"))
                .thenReturn(Optional.of(reference));
        when(mediaQueryService.findLegacyDetails(mediaId)).thenReturn(stored);

        ExternalMediaDetailsResponse response = externalMediaService.findDetails(
                ExternalSource.TMDB,
                MediaType.MOVIE,
                "550",
                "pt-BR"
        );

        assertThat(response).isSameAs(stored);
        verify(mediaQueryService).findLegacyDetails(mediaId);
        verifyNoInteractions(providerRegistry);
    }

    @Test
    void persistsExternalCreditsWhenImportingMedia() {
        ExternalMedia external = movie();
        UUID mediaId = UUID.randomUUID();
        when(externalReferenceRepository.findBySourceAndExternalId(ExternalSource.TMDB, "550"))
                .thenReturn(Optional.empty());
        when(providerRegistry.get(ExternalSource.TMDB, MediaType.MOVIE)).thenReturn(provider);
        when(provider.findById(MediaType.MOVIE, "550", "pt-BR")).thenReturn(Optional.of(external));
        when(wikidataClient.find(ExternalSource.TMDB, MediaType.MOVIE, "550", "pt-BR"))
                .thenReturn(Optional.empty());
        when(mediaRepository.save(any(Media.class))).thenAnswer(invocation -> {
            Media media = invocation.getArgument(0);
            media.setId(mediaId);
            return media;
        });

        var response = externalMediaService.importMedia(new ImportExternalMediaRequest(
                ExternalSource.TMDB, "550", MediaType.MOVIE));

        assertThat(response.id()).isEqualTo(mediaId);
        assertThat(response.creator()).isEqualTo("David Fincher");
        verify(mediaCreditService).save(argThat(media -> mediaId.equals(media.getId())), eq(external.credits()));
    }

    @Test
    void backfillsCreditsWhenImportingAnExistingMediaWithoutCredits() {
        UUID mediaId = UUID.randomUUID();
        Media media = new Media();
        media.setId(mediaId);
        media.setType(MediaType.MOVIE);
        media.setTitle("Fight Club");
        ExternalReference reference = new ExternalReference();
        reference.setMedia(media);
        reference.setSource(ExternalSource.TMDB);
        reference.setExternalId("550");
        ExternalMedia external = movie();

        when(externalReferenceRepository.findBySourceAndExternalId(ExternalSource.TMDB, "550"))
                .thenReturn(Optional.of(reference));
        when(mediaCreditService.summary(media)).thenReturn(MediaCreditService.CreditSummary.empty());
        when(providerRegistry.get(ExternalSource.TMDB, MediaType.MOVIE)).thenReturn(provider);
        when(provider.findById(MediaType.MOVIE, "550", "pt-BR")).thenReturn(Optional.of(external));

        var response = externalMediaService.importMedia(new ImportExternalMediaRequest(
                ExternalSource.TMDB, "550", MediaType.MOVIE));

        assertThat(response.id()).isEqualTo(mediaId);
        assertThat(response.creator()).isEqualTo("David Fincher");
        verify(mediaCreditService).save(media, external.credits());
    }

    @Test
    void givesNewAlbumTracksTheAlbumReleaseDate() {
        LocalDate futureRelease = LocalDate.now().plusDays(30);
        ExternalMedia album = new ExternalMedia(
                ExternalSource.MUSICBRAINZ,
                "album-1",
                MediaType.ALBUM,
                "Future Album",
                "Future Album",
                null,
                null,
                null,
                null,
                null,
                futureRelease,
                "en",
                "US",
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
                null,
                "ALBUM",
                1,
                "Artist",
                null,
                null,
                List.of(),
                List.of(new ExternalMedia.ExternalTrack(
                        "track-1", "Future Track", 1, 1, 180, false)),
                List.of(),
                List.of()
        );
        when(providerRegistry.get(ExternalSource.MUSICBRAINZ, MediaType.ALBUM))
                .thenReturn(provider);
        when(provider.findById(MediaType.ALBUM, "album-1", "pt-BR"))
                .thenReturn(Optional.of(album));
        when(wikidataClient.find(
                ExternalSource.MUSICBRAINZ, MediaType.ALBUM, "album-1", "pt-BR"))
                .thenReturn(Optional.empty());
        when(mediaRepository.save(any(Media.class))).thenAnswer(invocation -> {
            Media media = invocation.getArgument(0);
            if (media.getId() == null) media.setId(UUID.randomUUID());
            return media;
        });

        externalMediaService.importMedia(new ImportExternalMediaRequest(
                ExternalSource.MUSICBRAINZ, "album-1", MediaType.ALBUM));

        verify(mediaRepository, atLeastOnce()).save(argThat(media ->
                media.getType() == MediaType.TRACK
                        && futureRelease.equals(media.getReleaseDate())));
    }

    private ExternalMedia movie() {
        return new ExternalMedia(
                ExternalSource.TMDB, "550", MediaType.MOVIE, "Fight Club", "Fight Club", null, null,
                null, null, null, null, "en", "US", null, null, null, null, null, null, 139, null, null,
                null, null, null, null, null, null, "David Fincher", null, null,
                List.of(), List.of(), List.of(), List.of(
                        new ExternalMedia.ExternalCredit(
                                "7467", "David Fincher", CreditRole.DIRECTOR, null, 0,
                                "https://image.tmdb.org/t/p/w500/profile.jpg", ExternalSource.TMDB)
                )
        );
    }
}
