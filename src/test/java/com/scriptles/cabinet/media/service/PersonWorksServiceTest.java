package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.media.dto.response.PersonWorkResponse;
import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.MediaCredit;
import com.scriptles.cabinet.media.entity.Person;
import com.scriptles.cabinet.media.enums.CreditRole;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.external.AlbumCoverService;
import com.scriptles.cabinet.media.external.ExternalMedia;
import com.scriptles.cabinet.media.external.ExternalPersonWorksProvider;
import com.scriptles.cabinet.media.repository.ExternalReferenceRepository;
import com.scriptles.cabinet.media.repository.MediaCreditRepository;
import com.scriptles.cabinet.media.repository.PersonRepository;
import com.scriptles.cabinet.media.translation.CatalogLocaleResolver;
import com.scriptles.cabinet.media.translation.MediaTranslationResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersonWorksServiceTest {
    @Mock private PersonRepository personRepository;
    @Mock private MediaCreditRepository mediaCreditRepository;
    @Mock private ExternalReferenceRepository externalReferenceRepository;
    @Mock private PersonExternalIdentityResolver identityResolver;
    @Mock private PersonWorksCatalogService catalogService;
    @Mock private AlbumCoverService albumCoverService;
    @Mock private MediaTranslationResolver translationResolver;

    private PersonWorksService service;

    @BeforeEach
    void setUp() {
        service = new PersonWorksService(
                personRepository,
                mediaCreditRepository,
                externalReferenceRepository,
                identityResolver,
                catalogService,
                albumCoverService,
                new CatalogLocaleResolver(),
                translationResolver
        );
    }

    @Test
    void mergesLocalAndExternalWorksWithLocalPriorityAndPersonCredits() {
        UUID personId = UUID.randomUUID();
        Person person = person(personId, ExternalSource.TMDB, "7467");
        Media local = media(MediaType.MOVIE, "Fight Club", LocalDate.of(1999, 10, 15));
        MediaCredit actor = credit(person, local, CreditRole.ACTOR, "Tyler Durden");
        ExternalReference reference = reference(local, ExternalSource.TMDB, "550");
        ExternalMedia localPreview = externalMedia(
                ExternalSource.TMDB, MediaType.MOVIE, "550", "Fight Club", LocalDate.of(1999, 10, 15));
        ExternalMedia externalMovie = externalMedia(
                ExternalSource.TMDB, MediaType.MOVIE, "807", "Se7en", LocalDate.of(1995, 9, 22));

        when(personRepository.findById(personId)).thenReturn(Optional.of(person));
        when(mediaCreditRepository.findMediaByPersonId(
                personId, PageRequest.of(0, 200)))
                .thenReturn(new PageImpl<>(List.of(local), PageRequest.of(0, 200), 1));
        when(mediaCreditRepository.findAllByPersonIdAndMediaIdInOrderByPositionAsc(
                personId, List.of(local.getId())))
                .thenReturn(List.of(actor));
        when(externalReferenceRepository.findAllByMediaIdIn(List.of(local.getId())))
                .thenReturn(List.of(reference));
        when(translationResolver.resolveAll(anyList(), anyString())).thenReturn(Map.of());
        when(identityResolver.findExternalId(personId, ExternalSource.TMDB))
                .thenReturn(Optional.of("7467"));
        when(identityResolver.findExternalId(personId, ExternalSource.MUSICBRAINZ))
                .thenReturn(Optional.empty());
        when(catalogService.find(ExternalSource.TMDB, "7467", "pt-BR"))
                .thenReturn(new PersonWorksCatalogService.CatalogResult(List.of(
                        new ExternalPersonWorksProvider.Work(
                                localPreview, CreditRole.ACTOR, "Tyler Durden", 20),
                        new ExternalPersonWorksProvider.Work(
                                externalMovie, CreditRole.DIRECTOR, null, 10)
                ), false));

        var response = service.findWorks(personId, 0, 24, "pt-BR", null);

        assertThat(response.totalElements()).isEqualTo(2);
        assertThat(response.items()).extracting(PersonWorkResponse::title)
                .containsExactly("Fight Club", "Se7en");
        assertThat(response.items().getFirst().id()).isEqualTo(local.getId());
        assertThat(response.items().getFirst().imported()).isTrue();
        assertThat(response.items().getFirst().credits()).containsExactly(
                new PersonWorkResponse.CreditResponse(CreditRole.ACTOR, "Tyler Durden"));
        assertThat(response.items().get(1).id()).isNull();
        assertThat(response.items().get(1).credits()).containsExactly(
                new PersonWorkResponse.CreditResponse(CreditRole.DIRECTOR, null));
    }

    @Test
    void returnsMusicBrainzAlbumsForAnArtist() {
        UUID personId = UUID.randomUUID();
        Person person = person(personId, ExternalSource.MUSICBRAINZ, "artist-id");
        ExternalMedia album = externalMedia(
                ExternalSource.MUSICBRAINZ, MediaType.ALBUM, "album-id", "Album", LocalDate.of(2024, 1, 1));

        when(personRepository.findById(personId)).thenReturn(Optional.of(person));
        when(identityResolver.findExternalId(personId, ExternalSource.MUSICBRAINZ))
                .thenReturn(Optional.of("artist-id"));
        when(catalogService.find(ExternalSource.MUSICBRAINZ, "artist-id", "pt-BR"))
                .thenReturn(new PersonWorksCatalogService.CatalogResult(List.of(
                        new ExternalPersonWorksProvider.Work(album, CreditRole.ARTIST, null, 0)
                ), false));
        when(albumCoverService.findCoverUrl("album-id")).thenReturn("https://images.test/album.jpg");

        var response = service.findWorks(personId, 0, 24, "pt-BR", MediaType.ALBUM);

        assertThat(response.items()).singleElement().satisfies(work -> {
            assertThat(work.type()).isEqualTo(MediaType.ALBUM);
            assertThat(work.source()).isEqualTo(ExternalSource.MUSICBRAINZ);
            assertThat(work.imported()).isFalse();
            assertThat(work.coverUrl()).isEqualTo("https://images.test/album.jpg");
            assertThat(work.credits()).containsExactly(
                    new PersonWorkResponse.CreditResponse(CreditRole.ARTIST, null));
        });
        verify(catalogService).find(ExternalSource.MUSICBRAINZ, "artist-id", "pt-BR");
    }

    @Test
    void mergesDuplicateExternalWorksAndKeepsDistinctRoleAndCharacterCredits() {
        UUID personId = UUID.randomUUID();
        Person person = person(personId, ExternalSource.TMDB, "7467");
        ExternalMedia movie = externalMedia(
                ExternalSource.TMDB, MediaType.MOVIE, "550", "Fight Club", LocalDate.of(1999, 10, 15));
        when(personRepository.findById(personId)).thenReturn(Optional.of(person));
        when(mediaCreditRepository.findMediaByPersonId(personId, PageRequest.of(0, 200)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 200), 0));
        when(identityResolver.findExternalId(personId, ExternalSource.TMDB)).thenReturn(Optional.of("7467"));
        when(identityResolver.findExternalId(personId, ExternalSource.MUSICBRAINZ)).thenReturn(Optional.empty());
        when(catalogService.find(ExternalSource.TMDB, "7467", "pt-BR"))
                .thenReturn(new PersonWorksCatalogService.CatalogResult(List.of(
                        new ExternalPersonWorksProvider.Work(movie, CreditRole.ACTOR, "Tyler Durden", 20),
                        new ExternalPersonWorksProvider.Work(movie, CreditRole.ACTOR, "Tyler Durden", 20),
                        new ExternalPersonWorksProvider.Work(movie, CreditRole.PRODUCER, null, 10)
                ), false));

        var response = service.findWorks(personId, 0, 24, "pt-BR", null);

        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.items()).singleElement().satisfies(work -> {
            assertThat(work.title()).isEqualTo("Fight Club");
            assertThat(work.credits()).containsExactly(
                    new PersonWorkResponse.CreditResponse(CreditRole.ACTOR, "Tyler Durden"),
                    new PersonWorkResponse.CreditResponse(CreditRole.PRODUCER, null));
        });
    }

    private Person person(UUID id, ExternalSource source, String externalId) {
        Person person = new Person();
        person.setId(id);
        person.setName("A Person");
        person.setExternalSource(source);
        person.setExternalId(externalId);
        return person;
    }

    private Media media(MediaType type, String title, LocalDate releaseDate) {
        Media media = new Media();
        media.setId(UUID.randomUUID());
        media.setType(type);
        media.setTitle(title);
        media.setReleaseDate(releaseDate);
        return media;
    }

    private MediaCredit credit(Person person, Media media, CreditRole role, String characterName) {
        MediaCredit credit = new MediaCredit();
        credit.setPerson(person);
        credit.setMedia(media);
        credit.setRole(role);
        credit.setCharacterName(characterName);
        return credit;
    }

    private ExternalReference reference(Media media, ExternalSource source, String externalId) {
        ExternalReference reference = new ExternalReference();
        reference.setMedia(media);
        reference.setSource(source);
        reference.setExternalId(externalId);
        reference.setPrimaryReference(true);
        return reference;
    }

    private ExternalMedia externalMedia(
            ExternalSource source,
            MediaType type,
            String externalId,
            String title,
            LocalDate releaseDate
    ) {
        return new ExternalMedia(
                source, externalId, type, title, title, null, null, null, null, null,
                releaseDate, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                List.of(), List.of(), List.of(), List.of()
        );
    }
}
