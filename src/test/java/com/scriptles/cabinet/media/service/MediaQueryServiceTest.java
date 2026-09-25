package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.lists.repository.MediaListItemRepository;
import com.scriptles.cabinet.media.dto.response.ExternalMediaDetailsResponse;
import com.scriptles.cabinet.media.entity.AlbumDetails;
import com.scriptles.cabinet.media.entity.AlbumTrack;
import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.catalog.GenreCatalogService;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.MediaLike;
import com.scriptles.cabinet.media.entity.MovieDetails;
import com.scriptles.cabinet.media.entity.Rating;
import com.scriptles.cabinet.media.entity.Review;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.CreditRole;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.repository.AlbumDetailsRepository;
import com.scriptles.cabinet.media.repository.AlbumTrackRepository;
import com.scriptles.cabinet.media.repository.AlbumReleaseVersionRepository;
import com.scriptles.cabinet.media.repository.BookDetailsRepository;
import com.scriptles.cabinet.media.repository.ExternalReferenceRepository;
import com.scriptles.cabinet.media.repository.MediaLikeRepository;
import com.scriptles.cabinet.media.repository.MediaCommunityStatsRepository;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.media.repository.MovieDetailsRepository;
import com.scriptles.cabinet.media.repository.RatingRepository;
import com.scriptles.cabinet.media.repository.ReviewRepository;
import com.scriptles.cabinet.media.repository.SeriesEpisodeRepository;
import com.scriptles.cabinet.media.repository.SeriesDetailsRepository;
import com.scriptles.cabinet.media.repository.SeriesSeasonRepository;
import com.scriptles.cabinet.media.repository.TrackDetailsRepository;
import com.scriptles.cabinet.media.translation.CatalogLocaleResolver;
import com.scriptles.cabinet.media.translation.CatalogTranslationLoader;
import com.scriptles.cabinet.media.translation.MediaTranslationResolver;
import com.scriptles.cabinet.media.translation.ResolvedMediaTranslation;
import com.scriptles.cabinet.media.service.UserArtworkResolver;
import com.scriptles.cabinet.user.repository.UserMediaRepository;
import com.scriptles.cabinet.user.repository.UserMediaActivityRepository;
import com.scriptles.cabinet.user.repository.UserAlbumRotationRepository;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.entity.UserMedia;
import com.scriptles.cabinet.user.enums.ProfileActivityType;
import com.scriptles.cabinet.user.enums.UserMediaStatus;
import com.scriptles.cabinet.user.enums.Visibility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MediaQueryServiceTest {
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
    private AlbumReleaseVersionRepository albumReleaseVersionRepository;
    @Mock
    private SeriesSeasonRepository seriesSeasonRepository;
    @Mock
    private TrackDetailsRepository trackDetailsRepository;
    @Mock
    private RatingRepository ratingRepository;
    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private SeriesEpisodeRepository seriesEpisodeRepository;
    @Mock
    private MediaLikeRepository mediaLikeRepository;
    @Mock
    private MediaCommunityStatsRepository mediaCommunityStatsRepository;
    @Mock
    private MediaListItemRepository mediaListItemRepository;
    @Mock
    private UserMediaRepository userMediaRepository;
    @Mock
    private UserMediaActivityRepository userMediaActivityRepository;
    @Mock
    private UserAlbumRotationRepository userAlbumRotationRepository;
    @Mock
    private UserArtworkResolver userArtworkResolver;
    @Mock
    private MediaCreditService mediaCreditService;
    @Mock
    private RatingSummaryService ratingSummaryService;
    @Mock
    private CatalogLocaleResolver catalogLocaleResolver;
    @Mock
    private CatalogTranslationLoader catalogTranslationLoader;
    @Mock
    private MediaTranslationResolver mediaTranslationResolver;
    @Mock
    private MediaPublicVersionService mediaPublicVersionService;
    @Mock
    private AlbumMediaPageCursorCodec albumMediaPageCursorCodec;

    @Mock
    private GenreCatalogService genreCatalogService;

    @InjectMocks
    private MediaQueryService mediaQueryService;

    @BeforeEach
    void defaultCommunityAndVersionProjections() {
        lenient().when(mediaCommunityStatsRepository.findByMediaId(any()))
                .thenReturn(Optional.empty());
        lenient().when(mediaCommunityStatsRepository.findDistribution(any()))
                .thenReturn(List.of());
        lenient().when(mediaCommunityStatsRepository.aggregateByMediaIds(anyCollection()))
                .thenReturn(new MediaCommunityStatsRepository.CommunityAggregate(BigDecimal.ZERO, 0));
        lenient().when(mediaCommunityStatsRepository.aggregateDistributionByMediaIds(anyCollection()))
                .thenReturn(List.of());
        lenient().when(mediaPublicVersionService.currentVersion(any(), any()))
                .thenReturn(Optional.empty());
    }

    @Test
    void returnsOrderedAlbumTracksWithCommunityAndPersonalRatings() {
        UUID albumId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Media album = new Media();
        album.setId(albumId);
        album.setType(MediaType.ALBUM);

        AlbumTrack first = albumTrack(album, 1, 1, "First");
        AlbumTrack second = albumTrack(album, 1, 2, "Second");
        when(mediaRepository.findById(albumId)).thenReturn(Optional.of(album));
        when(albumTrackRepository.findAllByAlbumIdOrderByDiscNumberAscTrackNumberAsc(albumId))
                .thenReturn(List.of(first, second));
        when(ratingSummaryService.items(
                List.of(first.getTrackMedia().getId(), second.getTrackMedia().getId()), userId))
                .thenReturn(java.util.Map.of(
                        first.getTrackMedia().getId(), new RatingSummaryService.ItemStats(4.25, 8, 4.5),
                        second.getTrackMedia().getId(), new RatingSummaryService.ItemStats(3.75, 3, null)
                ));

        var response = mediaQueryService.findAlbumTracks(albumId, userId);

        assertThat(response.tracks()).extracting(ExternalMediaDetailsResponse.TrackResponse::title)
                .containsExactly("First", "Second");
        assertThat(response.tracks().getFirst().averageRating()).isEqualTo(4.25);
        assertThat(response.tracks().getFirst().ratingCount()).isEqualTo(8);
        assertThat(response.tracks().getFirst().myRating()).isEqualTo(4.5);
        assertThat(response.tracks().get(1).myRating()).isNull();
    }

    private AlbumTrack albumTrack(Media album, int disc, int number, String title) {
        Media trackMedia = new Media();
        trackMedia.setId(UUID.randomUUID());
        trackMedia.setType(MediaType.TRACK);
        trackMedia.setTitle(title);
        AlbumTrack track = new AlbumTrack();
        track.setAlbum(album);
        track.setTrackMedia(trackMedia);
        track.setDiscNumber(disc);
        track.setTrackNumber(number);
        track.setTitle(title);
        return track;
    }

    @Test
    void returnsDirectAndTrackBasedAlbumRatingsSeparately() {
        UUID albumId = UUID.randomUUID();
        UUID firstTrackId = UUID.randomUUID();
        UUID secondTrackId = UUID.randomUUID();
        Media album = new Media();
        album.setId(albumId);
        album.setType(MediaType.ALBUM);
        List<UUID> trackIds = List.of(firstTrackId, secondTrackId);
        when(mediaRepository.findById(albumId)).thenReturn(Optional.of(album));
        when(albumTrackRepository.findTrackMediaIdsByAlbumId(albumId)).thenReturn(trackIds);
        when(mediaCommunityStatsRepository.findByMediaId(albumId)).thenReturn(Optional.of(
                new MediaCommunityStatsRepository.CommunityStats(
                        3, new BigDecimal("10.5"), new BigDecimal("3.5"), 0, 0, 0)));
        when(mediaCommunityStatsRepository.aggregateByMediaIds(trackIds))
                .thenReturn(new MediaCommunityStatsRepository.CommunityAggregate(
                        new BigDecimal("12.99"), 3));
        when(mediaCommunityStatsRepository.aggregateDistributionByMediaIds(trackIds))
                .thenReturn(List.of(new MediaCommunityStatsRepository.RatingBucket(new BigDecimal("4.5"), 3)));

        var community = mediaQueryService.findCommunity(albumId);

        assertThat(community.averageRating()).isEqualTo(3.5);
        assertThat(community.ratingDistribution()).hasSize(10)
                .extracting(ExternalMediaDetailsResponse.RatingDistributionBucket::rating)
                .containsExactly(0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0, 4.5, 5.0);
        assertThat(community.childRatings().itemType()).isEqualTo(MediaType.TRACK);
        assertThat(community.childRatings().averageRating()).isEqualTo(4.33);
        assertThat(community.childRatings().ratingCount()).isEqualTo(3);
    }

    @Test
    void returnsEmptyViewerStateWithNoDiaryActivity() {
        UUID mediaId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Media media = media(mediaId, MediaType.MOVIE);
        stubUserStateBase(media, userId, Optional.empty(), Optional.empty(), Optional.empty(), false);
        when(userMediaActivityRepository.countByUserIdAndMediaIdAndTypeIn(
                userId, mediaId, diaryTypes())).thenReturn(0L);
        when(userMediaActivityRepository.findTopByUserIdAndMediaIdAndTypeInOrderByOccurredOnDescCreatedAtDesc(
                userId, mediaId, diaryTypes())).thenReturn(Optional.empty());

        var state = mediaQueryService.findUserState(mediaId, userId);

        assertThat(state.logCount()).isZero();
        assertThat(state.lastLoggedOn()).isNull();
        assertThat(state.listenCount()).isZero();
        assertThat(state.lastListenedOn()).isNull();
        assertThat(state.rating()).isNull();
        assertThat(state.reviewId()).isNull();
        assertThat(state.liked()).isFalse();
        assertThat(state.status()).isNull();
    }

    @Test
    void countsWatchAndRewatchActivityForAnyMediaVisibility() {
        UUID mediaId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Media media = media(mediaId, MediaType.MOVIE);
        LocalDate lastLoggedOn = LocalDate.of(2026, 9, 18);
        stubUserStateBase(media, userId, Optional.empty(), Optional.empty(), Optional.empty(), false);
        when(userMediaActivityRepository.countByUserIdAndMediaIdAndTypeIn(
                userId, mediaId, diaryTypes())).thenReturn(2L);
        when(userMediaActivityRepository.findTopByUserIdAndMediaIdAndTypeInOrderByOccurredOnDescCreatedAtDesc(
                userId, mediaId, diaryTypes())).thenReturn(Optional.of(activityOn(lastLoggedOn)));

        var state = mediaQueryService.findUserState(mediaId, userId);

        assertThat(state.logCount()).isEqualTo(2);
        assertThat(state.lastLoggedOn()).isEqualTo(lastLoggedOn);
        verify(userMediaActivityRepository).countByUserIdAndMediaIdAndTypeIn(userId, mediaId, diaryTypes());
        verify(userMediaActivityRepository)
                .findTopByUserIdAndMediaIdAndTypeInOrderByOccurredOnDescCreatedAtDesc(
                        userId, mediaId, diaryTypes());
    }

    @Test
    void countsOneDiaryLog() {
        UUID mediaId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Media media = media(mediaId, MediaType.BOOK);
        LocalDate loggedOn = LocalDate.of(2026, 9, 20);
        stubUserStateBase(media, userId, Optional.empty(), Optional.empty(), Optional.empty(), false);
        when(userMediaActivityRepository.countByUserIdAndMediaIdAndTypeIn(
                userId, mediaId, diaryTypes())).thenReturn(1L);
        when(userMediaActivityRepository.findTopByUserIdAndMediaIdAndTypeInOrderByOccurredOnDescCreatedAtDesc(
                userId, mediaId, diaryTypes())).thenReturn(Optional.of(activityOn(loggedOn)));

        var state = mediaQueryService.findUserState(mediaId, userId);

        assertThat(state.logCount()).isEqualTo(1);
        assertThat(state.lastLoggedOn()).isEqualTo(loggedOn);
    }

    @Test
    void keepsMusicListenCountWhileExposingGenericLogCount() {
        UUID mediaId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Media media = media(mediaId, MediaType.ALBUM);
        LocalDate lastLoggedOn = LocalDate.of(2026, 9, 19);
        LocalDate lastListenedOn = LocalDate.of(2026, 9, 17);
        stubUserStateBase(media, userId, Optional.empty(), Optional.empty(), Optional.empty(), false);
        when(userMediaActivityRepository.countByUserIdAndMediaIdAndTypeIn(
                userId, mediaId, diaryTypes())).thenReturn(3L);
        when(userMediaActivityRepository.findTopByUserIdAndMediaIdAndTypeInOrderByOccurredOnDescCreatedAtDesc(
                userId, mediaId, diaryTypes())).thenReturn(Optional.of(activityOn(lastLoggedOn)));
        when(userMediaActivityRepository.countByUserIdAndMediaIdAndTypeIn(
                userId, mediaId, listenTypes())).thenReturn(2L);
        when(userMediaActivityRepository.findTopByUserIdAndMediaIdAndTypeInOrderByOccurredOnDescCreatedAtDesc(
                userId, mediaId, listenTypes())).thenReturn(Optional.of(activityOn(lastListenedOn)));

        var state = mediaQueryService.findUserState(mediaId, userId);

        assertThat(state.logCount()).isEqualTo(3);
        assertThat(state.lastLoggedOn()).isEqualTo(lastLoggedOn);
        assertThat(state.listenCount()).isEqualTo(2);
        assertThat(state.lastListenedOn()).isEqualTo(lastListenedOn);
    }

    @Test
    void returnsCombinedLikePlannedRatingAndReviewState() {
        UUID mediaId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID reviewId = UUID.randomUUID();
        Media media = media(mediaId, MediaType.BOOK);
        UserMedia planned = new UserMedia();
        planned.setStatus(UserMediaStatus.PLANNED);
        Rating rating = new Rating();
        rating.setValue(new BigDecimal("4.5"));
        Review review = new Review();
        review.setId(reviewId);
        var state = viewerState(media, userId, Optional.of(planned), Optional.of(rating), Optional.of(review), true);

        assertThat(state.liked()).isTrue();
        assertThat(state.status()).isEqualTo(UserMediaStatus.PLANNED);
        assertThat(state.rating()).isEqualTo(4.5);
        assertThat(state.reviewId()).isEqualTo(reviewId);
    }

    @Test
    void returnsRatingOnlyViewerState() {
        Media media = media(UUID.randomUUID(), MediaType.MOVIE);
        Rating rating = new Rating();
        rating.setValue(new BigDecimal("3.5"));

        var state = viewerState(media, UUID.randomUUID(), Optional.empty(), Optional.of(rating), Optional.empty(), false);

        assertThat(state.rating()).isEqualTo(3.5);
        assertThat(state.liked()).isFalse();
        assertThat(state.status()).isNull();
        assertThat(state.reviewId()).isNull();
        assertThat(state.logCount()).isZero();
    }

    @Test
    void returnsLikeOnlyViewerState() {
        Media media = media(UUID.randomUUID(), MediaType.SERIES);

        var state = viewerState(media, UUID.randomUUID(), Optional.empty(), Optional.empty(), Optional.empty(), true);

        assertThat(state.liked()).isTrue();
        assertThat(state.rating()).isNull();
        assertThat(state.status()).isNull();
        assertThat(state.reviewId()).isNull();
    }

    @Test
    void returnsPlannedOnlyViewerState() {
        Media media = media(UUID.randomUUID(), MediaType.BOOK);
        UserMedia planned = new UserMedia();
        planned.setStatus(UserMediaStatus.PLANNED);

        var state = viewerState(media, UUID.randomUUID(), Optional.of(planned), Optional.empty(), Optional.empty(), false);

        assertThat(state.status()).isEqualTo(UserMediaStatus.PLANNED);
        assertThat(state.liked()).isFalse();
        assertThat(state.rating()).isNull();
        assertThat(state.reviewId()).isNull();
    }

    @Test
    void returnsReviewOnlyViewerState() {
        Media media = media(UUID.randomUUID(), MediaType.ALBUM);
        Review review = new Review();
        review.setId(UUID.randomUUID());

        var state = viewerState(media, UUID.randomUUID(), Optional.empty(), Optional.empty(), Optional.of(review), false);

        assertThat(state.reviewId()).isEqualTo(review.getId());
        assertThat(state.rating()).isNull();
        assertThat(state.liked()).isFalse();
        assertThat(state.status()).isNull();
    }

    private void stubUserStateBase(
            Media media,
            UUID userId,
            Optional<UserMedia> library,
            Optional<Rating> rating,
            Optional<Review> review,
            boolean liked
    ) {
        when(mediaRepository.findById(media.getId())).thenReturn(Optional.of(media));
        when(userMediaRepository.findByUserIdAndMediaId(userId, media.getId())).thenReturn(library);
        when(ratingRepository.findByUserIdAndMediaId(userId, media.getId())).thenReturn(rating);
        when(reviewRepository.findByUserIdAndMediaId(userId, media.getId())).thenReturn(review);
        when(userArtworkResolver.resolve(userId, media)).thenReturn(
                new UserArtworkResolver.ResolvedArtwork(null, null, false, false));
        when(mediaLikeRepository.existsByUserIdAndMediaId(userId, media.getId())).thenReturn(liked);
        when(mediaListItemRepository.findListIdsByMediaIdAndOwnerId(media.getId(), userId))
                .thenReturn(List.of());
        if (media.getType() == MediaType.ALBUM) {
            when(userAlbumRotationRepository.existsByUserIdAndAlbumId(userId, media.getId())).thenReturn(false);
        }
    }

    private com.scriptles.cabinet.media.dto.response.UserMediaStateResponse viewerState(
            Media media,
            UUID userId,
            Optional<UserMedia> library,
            Optional<Rating> rating,
            Optional<Review> review,
            boolean liked
    ) {
        stubUserStateBase(media, userId, library, rating, review, liked);
        when(userMediaActivityRepository.countByUserIdAndMediaIdAndTypeIn(
                userId, media.getId(), diaryTypes())).thenReturn(0L);
        when(userMediaActivityRepository.findTopByUserIdAndMediaIdAndTypeInOrderByOccurredOnDescCreatedAtDesc(
                userId, media.getId(), diaryTypes())).thenReturn(Optional.empty());
        return mediaQueryService.findUserState(media.getId(), userId);
    }

    private Media media(UUID id, MediaType type) {
        Media media = new Media();
        media.setId(id);
        media.setType(type);
        return media;
    }

    private EnumSet<ProfileActivityType> diaryTypes() {
        return EnumSet.of(ProfileActivityType.LOGGED, ProfileActivityType.RELOGGED,
                ProfileActivityType.WATCHED, ProfileActivityType.REWATCHED);
    }

    private EnumSet<ProfileActivityType> listenTypes() {
        return EnumSet.of(ProfileActivityType.LOGGED, ProfileActivityType.RELOGGED);
    }

    private com.scriptles.cabinet.user.entity.UserMediaActivity activityOn(LocalDate date) {
        var activity = new com.scriptles.cabinet.user.entity.UserMediaActivity();
        activity.setOccurredOn(date);
        return activity;
    }

    @Test
    void returnsEligibleEpisodeRatingsForSeries() {
        UUID seriesId = UUID.randomUUID();
        UUID episodeId = UUID.randomUUID();
        Media series = new Media();
        series.setId(seriesId);
        series.setType(MediaType.SERIES);
        List<UUID> episodeIds = List.of(episodeId);
        var childStats = RatingSummaryService.AggregateStats.empty();
        when(mediaRepository.findById(seriesId)).thenReturn(Optional.of(series));
        when(seriesEpisodeRepository.findEligibleEpisodeMediaIdsBySeriesId(
                seriesId, LocalDate.now())).thenReturn(episodeIds);

        var community = mediaQueryService.findCommunity(seriesId);

        assertThat(community.childRatings().itemType()).isEqualTo(MediaType.EPISODE);
        assertThat(community.childRatings().averageRating()).isNull();
        assertThat(community.childRatings().ratingDistribution()).hasSize(10);
    }

    @Test
    void returnsAnimatedCoverUrlInAlbumDetails() {
        UUID mediaId = UUID.randomUUID();
        Media media = new Media();
        media.setId(mediaId);
        media.setType(MediaType.ALBUM);
        media.setTitle("Discovery");

        AlbumDetails albumDetails = new AlbumDetails();
        albumDetails.setMedia(media);
        albumDetails.setAnimatedCoverUrl("https://example.com/discovery.gif");

        when(mediaRepository.findById(mediaId)).thenReturn(Optional.of(media));
        when(genreCatalogService.forMedia(org.mockito.ArgumentMatchers.eq(mediaId), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(List.of(new GenreCatalogService.GenreValue(UUID.randomUUID(), "Drama")));
        stubTranslation(media);
        when(externalReferenceRepository.findAllByMediaId(mediaId)).thenReturn(List.of());
        when(albumDetailsRepository.findById(mediaId)).thenReturn(Optional.of(albumDetails));
        when(albumTrackRepository.findAllByAlbumIdOrderByDiscNumberAscTrackNumberAsc(mediaId))
                .thenReturn(List.of());
        when(mediaCreditService.summary(media)).thenReturn(MediaCreditService.CreditSummary.empty());

        var response = mediaQueryService.findDetails(mediaId);

        assertThat(response.details()).isInstanceOfSatisfying(
                ExternalMediaDetailsResponse.AlbumDetails.class,
                details -> assertThat(details.animatedCoverUrl())
                        .isEqualTo("https://example.com/discovery.gif")
        );
    }

    @Test
    void buildsDetailsFromStoredEntitiesAndPrimaryReference() {
        UUID mediaId = UUID.randomUUID();
        Media media = new Media();
        media.setId(mediaId);
        media.setType(MediaType.MOVIE);
        media.setTitle("Fight Club");
        media.getGenres().add("Drama");

        ExternalReference reference = new ExternalReference();
        reference.setMedia(media);
        reference.setSource(ExternalSource.TMDB);
        reference.setExternalId("550");
        reference.setExternalUrl("https://www.themoviedb.org/movie/550");
        reference.setPrimaryReference(true);

        MovieDetails movieDetails = new MovieDetails();
        movieDetails.setMedia(media);
        movieDetails.setRuntimeMinutes(139);
        User liker = communityUser("ana", "https://example.com/ana.jpg");
        MediaLike mediaLike = new MediaLike();
        mediaLike.setUser(liker);
        User completer = communityUser("bia", "https://example.com/bia.jpg");
        UserMedia completed = new UserMedia();
        completed.setUser(completer);

        when(mediaRepository.findById(mediaId)).thenReturn(Optional.of(media));
        when(genreCatalogService.forMedia(org.mockito.ArgumentMatchers.eq(mediaId), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(List.of(new GenreCatalogService.GenreValue(UUID.randomUUID(), "Drama")));
        stubTranslation(media);
        when(externalReferenceRepository.findAllByMediaId(mediaId)).thenReturn(List.of(reference));
        when(movieDetailsRepository.findById(mediaId)).thenReturn(Optional.of(movieDetails));
        when(mediaLikeRepository.findTop3ByMediaIdOrderByLikedAtDescIdDesc(mediaId))
                .thenReturn(List.of(mediaLike));
        when(userMediaRepository
                .findTop3ByMediaIdAndStatusAndPrivateEntryFalseAndCompletedAtIsNotNullOrderByCompletedAtDescIdDesc(
                        mediaId, UserMediaStatus.COMPLETED))
                .thenReturn(List.of(completed));
        UUID personId = UUID.randomUUID();
        when(mediaCreditService.summary(media)).thenReturn(new MediaCreditService.CreditSummary(
                "David Fincher",
                "David Fincher",
                List.of(new MediaCreditService.CreditView(
                        personId,
                        "David Fincher",
                        CreditRole.DIRECTOR,
                        null,
                        0,
                        null,
                        ExternalSource.TMDB,
                        "7467"
                ))
        ));

        var response = mediaQueryService.findDetails(mediaId);
        var community = mediaQueryService.findCommunity(mediaId);

        assertThat(response.id()).isEqualTo(mediaId);
        assertThat(response.source()).isEqualTo(ExternalSource.TMDB);
        assertThat(response.externalId()).isEqualTo("550");
        assertThat(response.title()).isEqualTo("Fight Club");
        assertThat(response.genres()).extracting(genre -> genre.name()).containsExactly("Drama");
        assertThat(response.details())
                .isEqualTo(new com.scriptles.cabinet.media.dto.response.ExternalMediaDetailsResponse.MovieDetails(
                        139, null, null, "David Fincher"));
        assertThat(response.creator()).isEqualTo("David Fincher");
        assertThat(community.recentLikers()).singleElement().satisfies(user -> {
            assertThat(user.username()).isEqualTo("ana");
            assertThat(user.avatarUrl()).isEqualTo("https://example.com/ana.jpg");
        });
        assertThat(community.recentCompleters()).singleElement().satisfies(user -> {
            assertThat(user.username()).isEqualTo("bia");
            assertThat(user.avatarUrl()).isEqualTo("https://example.com/bia.jpg");
        });
        assertThat(community.childRatings()).isNull();
        assertThat(response.creator()).isEqualTo("David Fincher");
        assertThat(response.imported()).isTrue();
    }

    private User communityUser(String username, String avatarUrl) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername(username);
        user.setAvatarUlr(avatarUrl);
        return user;
    }

    private void stubTranslation(Media media) {
        when(catalogLocaleResolver.normalize("pt-BR")).thenReturn("pt-BR");
        when(mediaTranslationResolver.resolve(media, "pt-BR")).thenReturn(new ResolvedMediaTranslation(
                media.getId(),
                media.getTitle(),
                media.getDescription(),
                media.getTagline(),
                media.getCoverUrl(),
                "pt-BR",
                "pt-BR",
                false,
                false,
                com.scriptles.cabinet.media.enums.TranslationStatus.FALLBACK,
                ExternalSource.MANUAL
        ));
    }

    @Test
    void returnsOnlyTheRequestedCreditRoleWithPagination() {
        UUID mediaId = UUID.randomUUID();
        UUID personId = UUID.randomUUID();
        MediaCreditService.CreditView actor = new MediaCreditService.CreditView(
                personId,
                "Brad Pitt",
                CreditRole.ACTOR,
                "Tyler Durden",
                0,
                "https://image.tmdb.org/t/p/w500/pitt.jpg",
                ExternalSource.TMDB,
                "287"
        );
        when(mediaRepository.existsById(mediaId)).thenReturn(true);
        when(mediaCreditService.findByRole(mediaId, CreditRole.ACTOR, 0, 20))
                .thenReturn(new PageImpl<>(List.of(actor), PageRequest.of(0, 20), 1));

        var response = mediaQueryService.findCredits(mediaId, CreditRole.ACTOR, 0, 20);

        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.items()).singleElement().satisfies(credit -> {
            assertThat(credit.personId()).isEqualTo(personId);
            assertThat(credit.name()).isEqualTo("Brad Pitt");
            assertThat(credit.characterName()).isEqualTo("Tyler Durden");
            assertThat(credit.imageUrl()).endsWith("/pitt.jpg");
            assertThat(credit.role()).isEqualTo(CreditRole.ACTOR);
        });
    }
}
