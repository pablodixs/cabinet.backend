package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.lists.repository.MediaListItemRepository;
import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.MovieDetails;
import com.scriptles.cabinet.media.enums.ExternalSource;
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

        var response = mediaQueryService.findDetails(mediaId);

        assertThat(response.id()).isEqualTo(mediaId);
        assertThat(response.source()).isEqualTo(ExternalSource.TMDB);
        assertThat(response.externalId()).isEqualTo("550");
        assertThat(response.title()).isEqualTo("Fight Club");
        assertThat(response.genres()).extracting(genre -> genre.name()).containsExactly("Drama");
        assertThat(response.details())
                .isEqualTo(new com.scriptles.cabinet.media.dto.response.ExternalMediaDetailsResponse.MovieDetails(
                        139, null, null, null));
        assertThat(response.imported()).isTrue();
    }
}
