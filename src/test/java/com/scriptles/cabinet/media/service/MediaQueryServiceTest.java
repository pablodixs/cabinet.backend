package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.lists.repository.MediaListItemRepository;
import com.scriptles.cabinet.media.dto.response.ExternalMediaDetailsResponse;
import com.scriptles.cabinet.media.entity.AlbumDetails;
import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.MediaLike;
import com.scriptles.cabinet.media.entity.MovieDetails;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.CreditRole;
import com.scriptles.cabinet.media.enums.MediaType;
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
import com.scriptles.cabinet.media.translation.CatalogLocaleResolver;
import com.scriptles.cabinet.media.translation.CatalogTranslationLoader;
import com.scriptles.cabinet.media.translation.MediaTranslationResolver;
import com.scriptles.cabinet.media.translation.ResolvedMediaTranslation;
import com.scriptles.cabinet.user.repository.UserMediaRepository;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.entity.UserMedia;
import com.scriptles.cabinet.user.enums.UserMediaStatus;
import com.scriptles.cabinet.user.enums.Visibility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

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
    private MediaCreditService mediaCreditService;
    @Mock
    private RatingSummaryService ratingSummaryService;
    @Mock
    private CatalogLocaleResolver catalogLocaleResolver;
    @Mock
    private CatalogTranslationLoader catalogTranslationLoader;
    @Mock
    private MediaTranslationResolver mediaTranslationResolver;

    @InjectMocks
    private MediaQueryService mediaQueryService;

    @Test
    void returnsDirectAndTrackBasedAlbumRatingsSeparately() {
        UUID albumId = UUID.randomUUID();
        UUID firstTrackId = UUID.randomUUID();
        UUID secondTrackId = UUID.randomUUID();
        Media album = new Media();
        album.setId(albumId);
        album.setType(MediaType.ALBUM);
        List<UUID> trackIds = List.of(firstTrackId, secondTrackId);
        var childStats = new RatingSummaryService.AggregateStats(
                4.33,
                3,
                java.util.stream.IntStream.rangeClosed(1, 10)
                        .mapToObj(step -> new ExternalMediaDetailsResponse.RatingDistributionBucket(
                                step / 2.0, step == 10 ? 3 : 0))
                        .toList()
        );
        RatingRepository.MediaRatingProjection direct =
                org.mockito.Mockito.mock(RatingRepository.MediaRatingProjection.class);
        when(direct.getAverageRating()).thenReturn(3.5);
        when(mediaRepository.findById(albumId)).thenReturn(Optional.of(album));
        when(ratingRepository.summarizeRatings(List.of(albumId), Visibility.PUBLIC))
                .thenReturn(List.of(direct));
        when(albumTrackRepository.findTrackMediaIdsByAlbumId(albumId)).thenReturn(trackIds);
        when(ratingSummaryService.aggregate(trackIds)).thenReturn(childStats);

        var community = mediaQueryService.findCommunity(albumId);

        assertThat(community.averageRating()).isEqualTo(3.5);
        assertThat(community.childRatings().itemType()).isEqualTo(MediaType.TRACK);
        assertThat(community.childRatings().averageRating()).isEqualTo(4.33);
        assertThat(community.childRatings().ratingCount()).isEqualTo(3);
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
        when(ratingSummaryService.aggregate(episodeIds)).thenReturn(childStats);

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
        assertThat(response.credits()).singleElement().satisfies(credit -> {
            assertThat(credit.personId()).isEqualTo(personId);
            assertThat(credit.role()).isEqualTo(CreditRole.DIRECTOR);
        });
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
