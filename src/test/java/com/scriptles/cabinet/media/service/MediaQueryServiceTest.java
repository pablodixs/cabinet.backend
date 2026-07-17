package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.lists.repository.MediaListItemRepository;
import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.entity.Media;
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
import com.scriptles.cabinet.media.repository.ReviewRepository;
import com.scriptles.cabinet.media.repository.SeriesDetailsRepository;
import com.scriptles.cabinet.media.repository.SeriesSeasonRepository;
import com.scriptles.cabinet.media.repository.TrackDetailsRepository;
import com.scriptles.cabinet.user.repository.UserMediaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

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
    private ReviewRepository reviewRepository;
    @Mock
    private MediaLikeRepository mediaLikeRepository;
    @Mock
    private MediaListItemRepository mediaListItemRepository;
    @Mock
    private UserMediaRepository userMediaRepository;
    @Mock
    private MediaCreditService mediaCreditService;

    @InjectMocks
    private MediaQueryService mediaQueryService;

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

        when(mediaRepository.findById(mediaId)).thenReturn(Optional.of(media));
        when(externalReferenceRepository.findAllByMediaId(mediaId)).thenReturn(List.of(reference));
        when(movieDetailsRepository.findById(mediaId)).thenReturn(Optional.of(movieDetails));
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

        assertThat(response.id()).isEqualTo(mediaId);
        assertThat(response.source()).isEqualTo(ExternalSource.TMDB);
        assertThat(response.externalId()).isEqualTo("550");
        assertThat(response.title()).isEqualTo("Fight Club");
        assertThat(response.genres()).extracting(genre -> genre.name()).containsExactly("Drama");
        assertThat(response.details())
                .isEqualTo(new com.scriptles.cabinet.media.dto.response.ExternalMediaDetailsResponse.MovieDetails(
                        139, null, null, "David Fincher"));
        assertThat(response.creator()).isEqualTo("David Fincher");
        assertThat(response.credits()).singleElement().satisfies(credit -> {
            assertThat(credit.personId()).isEqualTo(personId);
            assertThat(credit.role()).isEqualTo(CreditRole.DIRECTOR);
        });
        assertThat(response.imported()).isTrue();
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
