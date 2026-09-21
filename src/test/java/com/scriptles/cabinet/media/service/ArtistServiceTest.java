package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.MediaCredit;
import com.scriptles.cabinet.media.entity.Person;
import com.scriptles.cabinet.media.enums.CreditRole;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.repository.MediaCreditRepository;
import com.scriptles.cabinet.media.repository.PersonRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArtistServiceTest {
    @Mock
    private PersonRepository personRepository;
    @Mock
    private MediaCreditRepository mediaCreditRepository;
    @Mock
    private RatingSummaryService ratingSummaryService;

    @InjectMocks
    private ArtistService artistService;

    @Test
    void returnsArtistProfileWithDistinctWorkCountAndOrderedRoles() {
        Person artist = artist("David Fincher");
        when(personRepository.findById(artist.getId())).thenReturn(Optional.of(artist));
        when(mediaCreditRepository.countDistinctMediaByPersonId(artist.getId())).thenReturn(4L);
        when(mediaCreditRepository.findDistinctRolesByPersonId(artist.getId()))
                .thenReturn(List.of(CreditRole.PRODUCER, CreditRole.DIRECTOR));
        when(mediaCreditRepository.findDistinctMediaIdsByPersonId(artist.getId()))
                .thenReturn(List.of());
        when(ratingSummaryService.aggregate(List.of()))
                .thenReturn(RatingSummaryService.AggregateStats.empty());

        var response = artistService.findDetails(artist.getId());

        assertThat(response.name()).isEqualTo("David Fincher");
        assertThat(response.workCount()).isEqualTo(4);
        assertThat(response.roles()).containsExactly(CreditRole.DIRECTOR, CreditRole.PRODUCER);
        assertThat(response.averageRating()).isNull();
        assertThat(response.source()).isEqualTo(ExternalSource.TMDB);
    }

    @Test
    void aggregatesCabinetRatingOncePerDistinctImportedWork() {
        Person artist = artist("David Fincher");
        UUID firstMediaId = UUID.randomUUID();
        UUID secondMediaId = UUID.randomUUID();
        List<UUID> distinctMediaIds = List.of(firstMediaId, secondMediaId);
        when(personRepository.findById(artist.getId())).thenReturn(Optional.of(artist));
        when(mediaCreditRepository.findDistinctRolesByPersonId(artist.getId())).thenReturn(List.of(CreditRole.ACTOR));
        when(mediaCreditRepository.findDistinctMediaIdsByPersonId(artist.getId()))
                .thenReturn(List.of(firstMediaId, firstMediaId, secondMediaId));
        when(mediaCreditRepository.countDistinctMediaByPersonId(artist.getId())).thenReturn(2L);
        when(ratingSummaryService.aggregate(distinctMediaIds)).thenReturn(
                new RatingSummaryService.AggregateStats(4.25, 3, List.of()));

        var response = artistService.findDetails(artist.getId());

        assertThat(response.averageRating()).isEqualTo(4.25);
        assertThat(response.workCount()).isEqualTo(2);
        // aggregate() uses the rating-weighted public-rating mean across distinct imported media IDs.
        verify(ratingSummaryService).aggregate(distinctMediaIds);
    }

    @Test
    void reportsUnavailableRatingForImportedWorksWithoutPublicRatings() {
        Person artist = artist("David Fincher");
        UUID mediaId = UUID.randomUUID();
        when(personRepository.findById(artist.getId())).thenReturn(Optional.of(artist));
        when(mediaCreditRepository.findDistinctRolesByPersonId(artist.getId())).thenReturn(List.of(CreditRole.DIRECTOR));
        when(mediaCreditRepository.findDistinctMediaIdsByPersonId(artist.getId())).thenReturn(List.of(mediaId));
        when(mediaCreditRepository.countDistinctMediaByPersonId(artist.getId())).thenReturn(1L);
        when(ratingSummaryService.aggregate(List.of(mediaId))).thenReturn(RatingSummaryService.AggregateStats.empty());

        var response = artistService.findDetails(artist.getId());

        assertThat(response.averageRating()).isNull();
    }

    @Test
    void returnsCabinetAverageForOneRatedImportedWork() {
        Person artist = artist("Ava DuVernay");
        UUID mediaId = UUID.randomUUID();
        when(personRepository.findById(artist.getId())).thenReturn(Optional.of(artist));
        when(mediaCreditRepository.findDistinctRolesByPersonId(artist.getId()))
                .thenReturn(List.of(CreditRole.DIRECTOR));
        when(mediaCreditRepository.findDistinctMediaIdsByPersonId(artist.getId())).thenReturn(List.of(mediaId));
        when(mediaCreditRepository.countDistinctMediaByPersonId(artist.getId())).thenReturn(1L);
        when(ratingSummaryService.aggregate(List.of(mediaId))).thenReturn(
                new RatingSummaryService.AggregateStats(4.5, 1, List.of()));

        var response = artistService.findDetails(artist.getId());

        assertThat(response.averageRating()).isEqualTo(4.5);
        verify(ratingSummaryService).aggregate(List.of(mediaId));
    }

    @Test
    void returnsEachWorkOnceAndKeepsAllArtistCredits() {
        Person artist = artist("David Fincher");
        Media movie = new Media();
        movie.setId(UUID.randomUUID());
        movie.setType(MediaType.MOVIE);
        movie.setTitle("Fight Club");
        movie.setReleaseDate(LocalDate.of(1999, 10, 15));

        MediaCredit director = credit(artist, movie, CreditRole.DIRECTOR);
        MediaCredit producer = credit(artist, movie, CreditRole.PRODUCER);

        when(personRepository.findById(artist.getId())).thenReturn(Optional.of(artist));
        when(mediaCreditRepository.findMediaByPersonId(
                org.mockito.ArgumentMatchers.eq(artist.getId()),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(movie)));
        when(mediaCreditRepository.findAllByPersonIdAndMediaIdInOrderByPositionAsc(
                artist.getId(),
                List.of(movie.getId())
        )).thenReturn(List.of(director, producer));

        var response = artistService.findWorks(artist.getId(), 0, 24);

        assertThat(response.items()).singleElement().satisfies(work -> {
            assertThat(work.mediaId()).isEqualTo(movie.getId());
            assertThat(work.title()).isEqualTo("Fight Club");
            assertThat(work.credits()).extracting(credit -> credit.role())
                    .containsExactly(CreditRole.DIRECTOR, CreditRole.PRODUCER);
        });
    }

    @Test
    void hidesWhetherAnUnknownArtistHasCredits() {
        UUID artistId = UUID.randomUUID();
        when(personRepository.findById(artistId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> artistService.findWorks(artistId, 0, 24))
                .isInstanceOf(ApiException.class)
                .hasMessage("Artista não encontrado");

        verify(mediaCreditRepository, never()).findMediaByPersonId(
                org.mockito.ArgumentMatchers.eq(artistId),
                any(Pageable.class)
        );
    }

    private Person artist(String name) {
        Person person = new Person();
        person.setId(UUID.randomUUID());
        person.setName(name);
        person.setExternalSource(ExternalSource.TMDB);
        person.setExternalId("7467");
        return person;
    }

    private MediaCredit credit(Person artist, Media media, CreditRole role) {
        MediaCredit credit = new MediaCredit();
        credit.setPerson(artist);
        credit.setMedia(media);
        credit.setRole(role);
        return credit;
    }
}
