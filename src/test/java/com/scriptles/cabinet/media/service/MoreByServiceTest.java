package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.media.dto.response.MediaSearchItemResponse;
import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.MediaCredit;
import com.scriptles.cabinet.media.entity.Person;
import com.scriptles.cabinet.media.entity.PersonExternalReference;
import com.scriptles.cabinet.media.enums.CreditRole;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.enums.MoreByState;
import com.scriptles.cabinet.media.external.AlbumCoverService;
import com.scriptles.cabinet.media.external.ExternalMedia;
import com.scriptles.cabinet.media.external.ExternalPersonWorksProvider;
import com.scriptles.cabinet.media.repository.ExternalReferenceRepository;
import com.scriptles.cabinet.media.repository.MediaCreditRepository;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.media.repository.PersonExternalReferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MoreByServiceTest {
    @Mock private MediaRepository mediaRepository;
    @Mock private MediaCreditRepository mediaCreditRepository;
    @Mock private PersonExternalReferenceRepository personExternalReferenceRepository;
    @Mock private ExternalReferenceRepository externalReferenceRepository;
    @Mock private PersonWorksCatalogService catalogService;
    @Mock private MediaSearchItemAssembler itemAssembler;
    @Mock private AlbumCoverService albumCoverService;

    private MoreByService service;

    @BeforeEach
    void setUp() {
        service = new MoreByService(
                mediaRepository,
                mediaCreditRepository,
                personExternalReferenceRepository,
                externalReferenceRepository,
                catalogService,
                itemAssembler,
                albumCoverService
        );
    }

    @Test
    void combinesTmdbDirectorMoviesAndExcludesCurrentMedia() {
        Media current = media(MediaType.MOVIE, "Fight Club", LocalDate.of(1999, 10, 15));
        Person director = person("David Fincher", ExternalSource.TMDB, "7467");
        MediaCredit principal = credit(current, director, CreditRole.DIRECTOR, ExternalSource.TMDB, "7467");
        Media local = media(MediaType.MOVIE, "Local film", LocalDate.of(1990, 1, 1));
        PersonExternalReference personReference = personReference(director, ExternalSource.TMDB, "7467");
        ExternalReference currentReference = mediaReference(current, ExternalSource.TMDB, "550");
        ExternalMedia currentExternal = externalMedia(
                ExternalSource.TMDB, MediaType.MOVIE, "550", "Fight Club", LocalDate.of(1999, 10, 15));
        ExternalMedia se7en = externalMedia(
                ExternalSource.TMDB, MediaType.MOVIE, "807", "Se7en", LocalDate.of(1995, 9, 22));
        MediaSearchItemResponse externalItem = item(
                null, ExternalSource.TMDB, "807", MediaType.MOVIE, "Se7en", LocalDate.of(1995, 9, 22));
        MediaSearchItemResponse localItem = item(
                local.getId(), ExternalSource.MANUAL, local.getId().toString(),
                MediaType.MOVIE, "Local film", LocalDate.of(1990, 1, 1));

        when(mediaRepository.findById(current.getId())).thenReturn(Optional.of(current));
        when(mediaCreditRepository.findPrincipalByMediaIdAndRole(
                current.getId(), CreditRole.DIRECTOR, PageRequest.of(0, 1))).thenReturn(List.of(principal));
        when(mediaCreditRepository.findLocalWorks(
                director.getId(), CreditRole.DIRECTOR, MediaType.MOVIE,
                current.getId(), PageRequest.of(0, 120))).thenReturn(List.of(local));
        when(itemAssembler.fromImported(List.of(local))).thenReturn(List.of(localItem));
        when(personExternalReferenceRepository.findFirstByPersonIdAndSource(
                director.getId(), ExternalSource.TMDB)).thenReturn(Optional.of(personReference));
        when(catalogService.find(ExternalSource.TMDB, "7467", "pt-BR"))
                .thenReturn(new PersonWorksCatalogService.CatalogResult(List.of(
                        new ExternalPersonWorksProvider.Work(currentExternal, 30),
                        new ExternalPersonWorksProvider.Work(se7en, 20)
                ), false));
        when(externalReferenceRepository.findAllByMediaId(current.getId()))
                .thenReturn(List.of(currentReference));
        when(itemAssembler.fromExternal(anyList())).thenReturn(List.of(externalItem));

        var response = service.find(current.getId(), "pt-BR", 12);

        assertThat(response.state()).isEqualTo(MoreByState.READY);
        assertThat(response.role()).isEqualTo(CreditRole.DIRECTOR);
        assertThat(response.person().id()).isEqualTo(director.getId());
        assertThat(response.incomplete()).isFalse();
        assertThat(response.items()).extracting(MediaSearchItemResponse::title)
                .containsExactly("Se7en", "Local film");
        verify(itemAssembler).fromExternal(org.mockito.ArgumentMatchers.argThat(values ->
                values.size() == 1
                        && values.getFirst().externalId().equals("807")
                        && values.getFirst().creator().equals("David Fincher")));
        verify(albumCoverService, never()).findCoverUrl(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void trackReturnsAlbumsSortedTogetherByReleaseDate() {
        Media current = media(MediaType.TRACK, "A Song", LocalDate.of(2024, 1, 1));
        Person artist = person("An Artist", ExternalSource.MUSICBRAINZ, "artist-id");
        MediaCredit principal = credit(
                current, artist, CreditRole.ARTIST, ExternalSource.MUSICBRAINZ, "artist-id");
        Media localAlbum = media(MediaType.ALBUM, "New local album", LocalDate.of(2025, 1, 1));
        PersonExternalReference reference = personReference(
                artist, ExternalSource.MUSICBRAINZ, "artist-id");
        ExternalMedia oldAlbum = externalMedia(
                ExternalSource.MUSICBRAINZ, MediaType.ALBUM, "album-id", "Old album",
                LocalDate.of(2020, 1, 1));
        MediaSearchItemResponse localItem = item(
                localAlbum.getId(), ExternalSource.MANUAL, localAlbum.getId().toString(),
                MediaType.ALBUM, localAlbum.getTitle(), localAlbum.getReleaseDate());
        MediaSearchItemResponse externalItem = item(
                null, ExternalSource.MUSICBRAINZ, "album-id", MediaType.ALBUM,
                "Old album", LocalDate.of(2020, 1, 1));

        when(mediaRepository.findById(current.getId())).thenReturn(Optional.of(current));
        when(mediaCreditRepository.findPrincipalByMediaIdAndRole(
                current.getId(), CreditRole.ARTIST, PageRequest.of(0, 1))).thenReturn(List.of(principal));
        when(mediaCreditRepository.findLocalWorks(
                artist.getId(), CreditRole.ARTIST, MediaType.ALBUM,
                current.getId(), PageRequest.of(0, 120))).thenReturn(List.of(localAlbum));
        when(itemAssembler.fromImported(List.of(localAlbum))).thenReturn(List.of(localItem));
        when(personExternalReferenceRepository.findFirstByPersonIdAndSource(
                artist.getId(), ExternalSource.MUSICBRAINZ)).thenReturn(Optional.of(reference));
        when(catalogService.find(ExternalSource.MUSICBRAINZ, "artist-id", "pt-BR"))
                .thenReturn(new PersonWorksCatalogService.CatalogResult(List.of(
                        new ExternalPersonWorksProvider.Work(oldAlbum, 0)), false));
        when(albumCoverService.findCoverUrl("album-id")).thenReturn("https://images.test/album.jpg");
        when(itemAssembler.fromExternal(anyList())).thenReturn(List.of(externalItem));

        var response = service.find(current.getId(), "pt-BR", 12);

        assertThat(response.items()).extracting(MediaSearchItemResponse::title)
                .containsExactly("New local album", "Old album");
        verify(mediaCreditRepository).findLocalWorks(
                artist.getId(), CreditRole.ARTIST, MediaType.ALBUM,
                current.getId(), PageRequest.of(0, 120));
        verify(albumCoverService).findCoverUrl("album-id");
    }

    @Test
    void returnsLocalFallbackAsIncompleteWhenExternalIdentityIsMissing() {
        Media current = media(MediaType.MOVIE, "Manual movie", LocalDate.now());
        Person director = person("A Director", ExternalSource.MANUAL, null);
        MediaCredit principal = credit(current, director, CreditRole.DIRECTOR, ExternalSource.MANUAL, null);
        Media local = media(MediaType.MOVIE, "Another movie", LocalDate.now().minusYears(1));
        MediaSearchItemResponse localItem = item(
                local.getId(), ExternalSource.MANUAL, local.getId().toString(),
                MediaType.MOVIE, local.getTitle(), local.getReleaseDate());

        when(mediaRepository.findById(current.getId())).thenReturn(Optional.of(current));
        when(mediaCreditRepository.findPrincipalByMediaIdAndRole(
                current.getId(), CreditRole.DIRECTOR, PageRequest.of(0, 1))).thenReturn(List.of(principal));
        when(mediaCreditRepository.findLocalWorks(
                director.getId(), CreditRole.DIRECTOR, MediaType.MOVIE,
                current.getId(), PageRequest.of(0, 120))).thenReturn(List.of(local));
        when(itemAssembler.fromImported(List.of(local))).thenReturn(List.of(localItem));

        var response = service.find(current.getId(), "pt-BR", 12);

        assertThat(response.state()).isEqualTo(MoreByState.READY);
        assertThat(response.incomplete()).isTrue();
        assertThat(response.items()).containsExactly(localItem);
        verify(catalogService, never()).find(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void returnsUnsupportedForBooksWithoutLookingUpCredits() {
        Media book = media(MediaType.BOOK, "Book", LocalDate.now());
        when(mediaRepository.findById(book.getId())).thenReturn(Optional.of(book));

        var response = service.find(book.getId(), "pt-BR", 12);

        assertThat(response.state()).isEqualTo(MoreByState.UNSUPPORTED);
        assertThat(response.role()).isNull();
        assertThat(response.person()).isNull();
        assertThat(response.items()).isEmpty();
        verify(mediaCreditRepository, never()).findPrincipalByMediaIdAndRole(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void returnsMediaNotFound() {
        UUID mediaId = UUID.randomUUID();
        when(mediaRepository.findById(mediaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.find(mediaId, "pt-BR", 12))
                .isInstanceOf(ApiException.class)
                .hasMessage("Mídia não encontrada");
    }

    private Media media(MediaType type, String title, LocalDate releaseDate) {
        Media media = new Media();
        media.setId(UUID.randomUUID());
        media.setType(type);
        media.setTitle(title);
        media.setReleaseDate(releaseDate);
        return media;
    }

    private Person person(String name, ExternalSource source, String externalId) {
        Person person = new Person();
        person.setId(UUID.randomUUID());
        person.setName(name);
        person.setExternalSource(source);
        person.setExternalId(externalId);
        return person;
    }

    private MediaCredit credit(
            Media media,
            Person person,
            CreditRole role,
            ExternalSource source,
            String externalId
    ) {
        MediaCredit credit = new MediaCredit();
        credit.setMedia(media);
        credit.setPerson(person);
        credit.setRole(role);
        credit.setSource(source);
        credit.setExternalId(externalId);
        return credit;
    }

    private PersonExternalReference personReference(
            Person person,
            ExternalSource source,
            String externalId
    ) {
        PersonExternalReference reference = new PersonExternalReference();
        reference.setPerson(person);
        reference.setSource(source);
        reference.setExternalId(externalId);
        return reference;
    }

    private ExternalReference mediaReference(Media media, ExternalSource source, String externalId) {
        ExternalReference reference = new ExternalReference();
        reference.setMedia(media);
        reference.setSource(source);
        reference.setExternalId(externalId);
        return reference;
    }

    private MediaSearchItemResponse item(
            UUID id,
            ExternalSource source,
            String externalId,
            MediaType type,
            String title,
            LocalDate releaseDate
    ) {
        return new MediaSearchItemResponse(
                id, externalId, source, type, title, null, null, null, releaseDate,
                id != null, null, 0
        );
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
