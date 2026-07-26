package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.media.dto.response.HeaderSearchResponse;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.Person;
import com.scriptles.cabinet.media.enums.HeaderSearchEntityType;
import com.scriptles.cabinet.media.enums.HeaderSearchScope;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.media.repository.PersonRepository;
import com.scriptles.cabinet.media.translation.CatalogLocaleResolver;
import com.scriptles.cabinet.media.translation.MediaTranslationResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HeaderSearchServiceTest {
    @Mock
    private MediaRepository mediaRepository;
    @Mock
    private PersonRepository personRepository;
    @Mock
    private MediaCreditService mediaCreditService;
    @Mock
    private MediaTranslationResolver translationResolver;
    @Mock
    private CatalogLocaleResolver localeResolver;

    private HeaderSearchService service;

    @BeforeEach
    void setUp() {
        service = new HeaderSearchService(
                mediaRepository,
                personRepository,
                mediaCreditService,
                translationResolver,
                localeResolver
        );
        org.mockito.Mockito.lenient().when(localeResolver.normalize("pt-BR")).thenReturn("pt-BR");
        org.mockito.Mockito.lenient().when(translationResolver.resolveAll(
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyString()
        )).thenReturn(Map.of());
    }

    @Test
    void mergesMediaAndArtistsByRelevanceAndReturnsAtMostFiveItems() {
        Media prefixMedia = media("Matrix Reloaded", "The Matrix Reloaded", 2003);
        Media partialMedia = media("Animatrix", null, 2003);
        Person exactArtist = artist("Matrix");
        Person partialArtist = artist("The Matrix Band");

        when(mediaRepository.findHeaderSearchCandidates(
                "matrix", null, PageRequest.of(0, 5)))
                .thenReturn(List.of(prefixMedia, partialMedia));
        when(personRepository.findHeaderSearchCandidates(
                "matrix", null, PageRequest.of(0, 5)))
                .thenReturn(List.of(exactArtist, partialArtist));
        when(mediaCreditService.summaries(List.of(prefixMedia, partialMedia))).thenReturn(Map.of(
                prefixMedia.getId(),
                new MediaCreditService.CreditSummary("Lana Wachowski", null, List.of())
        ));

        HeaderSearchResponse response = service.search(" matrix ", HeaderSearchScope.ALL, null);

        assertThat(response.items()).hasSize(4);
        assertThat(response.items()).extracting(item -> item.id()).containsExactly(
                exactArtist.getId(),
                prefixMedia.getId(),
                partialArtist.getId(),
                partialMedia.getId()
        );
        assertThat(response.items().get(1)).satisfies(item -> {
            assertThat(item.entityType()).isEqualTo(HeaderSearchEntityType.MEDIA);
            assertThat(item.creator()).isEqualTo("Lana Wachowski");
            assertThat(item.year()).isEqualTo(2003);
            assertThat(item.coverUrl()).isEqualTo("https://covers/Matrix_Reloaded");
        });
        assertThat(response.items().getFirst().creator()).isNull();
        assertThat(response.items().getFirst().year()).isNull();
    }

    @Test
    void appliesTypeToMediaAndArtistCandidates() {
        when(mediaRepository.findHeaderSearchCandidates(
                "duna", MediaType.BOOK.name(), PageRequest.of(0, 5)))
                .thenReturn(List.of());
        when(personRepository.findHeaderSearchCandidates(
                "duna", MediaType.BOOK.name(), PageRequest.of(0, 5)))
                .thenReturn(List.of());

        service.search("duna", HeaderSearchScope.ALL, MediaType.BOOK);

        verify(mediaRepository).findHeaderSearchCandidates(
                "duna", MediaType.BOOK.name(), PageRequest.of(0, 5));
        verify(personRepository).findHeaderSearchCandidates(
                "duna", MediaType.BOOK.name(), PageRequest.of(0, 5));
    }

    @Test
    void artistScopeSkipsMediaQueryAndEnrichment() {
        when(personRepository.findHeaderSearchCandidates(
                "bjork", null, PageRequest.of(0, 5)))
                .thenReturn(List.of(artist("Björk")));

        HeaderSearchResponse response = service.search("bjork", HeaderSearchScope.ARTIST, null);

        assertThat(response.items()).singleElement()
                .extracting(item -> item.entityType())
                .isEqualTo(HeaderSearchEntityType.ARTIST);
        verify(mediaRepository, never()).findHeaderSearchCandidates(
                "bjork", null, PageRequest.of(0, 5));
        verify(mediaCreditService, never()).summaries(org.mockito.ArgumentMatchers.anyList());
    }

    private Media media(String title, String originalTitle, int year) {
        Media media = new Media();
        media.setId(UUID.randomUUID());
        media.setType(MediaType.MOVIE);
        media.setTitle(title);
        media.setOriginalTitle(originalTitle);
        media.setCoverUrl("https://covers/" + title.replace(' ', '_'));
        media.setReleaseDate(LocalDate.of(year, 1, 1));
        return media;
    }

    private Person artist(String name) {
        Person person = new Person();
        person.setId(UUID.randomUUID());
        person.setName(name);
        person.setImageUrl("https://artists/" + name.replace(' ', '_'));
        return person;
    }
}
