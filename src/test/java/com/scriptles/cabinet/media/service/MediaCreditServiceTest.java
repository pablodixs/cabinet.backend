package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.MediaCredit;
import com.scriptles.cabinet.media.entity.Person;
import com.scriptles.cabinet.media.enums.CreditRole;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.external.ExternalMedia;
import com.scriptles.cabinet.media.repository.MediaCreditRepository;
import com.scriptles.cabinet.media.repository.PersonExternalReferenceRepository;
import com.scriptles.cabinet.media.repository.PersonRepository;
import com.scriptles.cabinet.media.repository.RatingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class MediaCreditServiceTest {
    @Autowired
    private TestEntityManager entityManager;
    @Autowired
    private PersonRepository personRepository;
    @Autowired
    private MediaCreditRepository mediaCreditRepository;
    @Autowired
    private PersonExternalReferenceRepository personExternalReferenceRepository;
    @Autowired
    private RatingRepository ratingRepository;

    private MediaCreditService mediaCreditService;
    private ArtistService artistService;

    @BeforeEach
    void setUp() {
        ArtistIdentityEnricher identityEnricher = (source, externalId) -> switch (externalId) {
            case "same-tmdb", "same-mb" -> Optional.of("Q123");
            case "legacy-tmdb", "legacy-mb" -> Optional.of("Q999");
            case "different-tmdb" -> Optional.of("Q111");
            case "different-mb" -> Optional.of("Q222");
            case "tmdb-homonym-1" -> Optional.of("Q333");
            case "tmdb-homonym-2" -> Optional.of("Q444");
            default -> Optional.empty();
        };
        PersonIdentityService personIdentityService = new PersonIdentityService(
                personRepository,
                personExternalReferenceRepository,
                mediaCreditRepository,
                identityEnricher
        );
        mediaCreditService = new MediaCreditService(personIdentityService, mediaCreditRepository);
        artistService = new ArtistService(
                personRepository, mediaCreditRepository, new RatingSummaryService(ratingRepository));
    }

    @Test
    void persistsCreditsAndReusesTheSameExternalPersonAcrossRoles() {
        Media movie = new Media();
        movie.setType(MediaType.MOVIE);
        movie.setTitle("Fight Club");
        entityManager.persist(movie);

        List<ExternalMedia.ExternalCredit> credits = List.of(
                credit("7467", "David Fincher", CreditRole.DIRECTOR, 0),
                credit("7467", "David Fincher", CreditRole.PRODUCER, 1)
        );
        mediaCreditService.save(movie, credits);
        mediaCreditService.save(movie, credits);
        entityManager.flush();
        entityManager.clear();

        Media storedMovie = entityManager.find(Media.class, movie.getId());
        MediaCreditService.CreditSummary summary = mediaCreditService.summary(storedMovie);

        assertThat(personRepository.count()).isEqualTo(1);
        assertThat(mediaCreditRepository.count()).isEqualTo(2);
        assertThat(summary.creator()).isEqualTo("David Fincher");
        assertThat(summary.director()).isEqualTo("David Fincher");
        assertThat(summary.credits()).extracting(MediaCreditService.CreditView::role)
                .containsExactly(CreditRole.DIRECTOR, CreditRole.PRODUCER);
        assertThat(summary.credits()).allSatisfy(credit -> {
            assertThat(credit.personId()).isNotNull();
            assertThat(credit.externalId()).isEqualTo("7467");
            assertThat(credit.source()).isEqualTo(ExternalSource.TMDB);
        });
    }

    @Test
    void filtersAndPaginatesCreditsByRoleInTheDatabase() {
        Media movie = media(MediaType.MOVIE, "Fight Club");
        mediaCreditService.save(movie, List.of(
                new ExternalMedia.ExternalCredit(
                        "287", "Brad Pitt", CreditRole.ACTOR, "Tyler Durden", 0,
                        "https://image.tmdb.org/t/p/w500/pitt.jpg", ExternalSource.TMDB),
                new ExternalMedia.ExternalCredit(
                        "819", "Edward Norton", CreditRole.ACTOR, "The Narrator", 1,
                        "https://image.tmdb.org/t/p/w500/norton.jpg", ExternalSource.TMDB),
                credit("7467", "David Fincher", CreditRole.DIRECTOR, 0)
        ));
        entityManager.flush();
        entityManager.clear();

        var firstPage = mediaCreditService.findByRole(movie.getId(), CreditRole.ACTOR, 0, 1);
        var secondPage = mediaCreditService.findByRole(movie.getId(), CreditRole.ACTOR, 1, 1);

        assertThat(firstPage.getTotalElements()).isEqualTo(2);
        assertThat(firstPage.getContent()).singleElement().satisfies(actor -> {
            assertThat(actor.name()).isEqualTo("Brad Pitt");
            assertThat(actor.characterName()).isEqualTo("Tyler Durden");
            assertThat(actor.imageUrl()).endsWith("/pitt.jpg");
        });
        assertThat(secondPage.getContent()).singleElement().satisfies(actor ->
                assertThat(actor.name()).isEqualTo("Edward Norton"));
    }

    @Test
    void artistWorksAreDistinctAndOrderedByNewestRelease() {
        Media olderMovie = new Media();
        olderMovie.setType(MediaType.MOVIE);
        olderMovie.setTitle("Fight Club");
        olderMovie.setReleaseDate(LocalDate.of(1999, 10, 15));
        entityManager.persist(olderMovie);

        Media newerMovie = new Media();
        newerMovie.setType(MediaType.MOVIE);
        newerMovie.setTitle("The Killer");
        newerMovie.setReleaseDate(LocalDate.of(2023, 10, 27));
        entityManager.persist(newerMovie);

        mediaCreditService.save(olderMovie, List.of(
                credit("7467", "David Fincher", CreditRole.DIRECTOR, 0),
                credit("7467", "David Fincher", CreditRole.PRODUCER, 1)
        ));
        mediaCreditService.save(newerMovie, List.of(
                credit("7467", "David Fincher", CreditRole.DIRECTOR, 0)
        ));
        entityManager.flush();
        entityManager.clear();

        var artist = personRepository.findByExternalSourceAndExternalId(
                ExternalSource.TMDB,
                "7467"
        ).orElseThrow();
        var profile = artistService.findDetails(artist.getId());
        var works = artistService.findWorks(artist.getId(), 0, 24);

        assertThat(profile.workCount()).isEqualTo(2);
        assertThat(profile.roles()).containsExactly(CreditRole.DIRECTOR, CreditRole.PRODUCER);
        assertThat(works.items()).extracting(work -> work.title())
                .containsExactly("The Killer", "Fight Club");
        assertThat(works.items().get(1).credits()).extracting(credit -> credit.role())
                .containsExactly(CreditRole.DIRECTOR, CreditRole.PRODUCER);
    }

    @Test
    void mergesTmdbAndMusicBrainzArtistsWhenWikidataIdentityMatches() {
        Media movie = media(MediaType.MOVIE, "A Movie");
        Media album = media(MediaType.ALBUM, "An Album");

        mediaCreditService.save(movie, List.of(credit(
                "same-tmdb", "Shared Artist", CreditRole.COMPOSER, 0, ExternalSource.TMDB)));
        mediaCreditService.save(album, List.of(credit(
                "same-mb", "Shared Artist", CreditRole.ARTIST, 0, ExternalSource.MUSICBRAINZ)));
        entityManager.flush();
        entityManager.clear();

        var tmdbReference = personExternalReferenceRepository
                .findBySourceAndExternalId(ExternalSource.TMDB, "same-tmdb")
                .orElseThrow();
        var musicBrainzReference = personExternalReferenceRepository
                .findBySourceAndExternalId(ExternalSource.MUSICBRAINZ, "same-mb")
                .orElseThrow();
        var wikidataReference = personExternalReferenceRepository
                .findBySourceAndExternalId(ExternalSource.WIKIDATA, "Q123")
                .orElseThrow();

        assertThat(personRepository.count()).isEqualTo(1);
        assertThat(tmdbReference.getPerson().getId())
                .isEqualTo(musicBrainzReference.getPerson().getId())
                .isEqualTo(wikidataReference.getPerson().getId());
        assertThat(artistService.findDetails(tmdbReference.getPerson().getId()).workCount()).isEqualTo(2);
        assertThat(artistService.findWorks(tmdbReference.getPerson().getId(), 0, 24).items())
                .extracting(work -> work.title())
                .containsExactlyInAnyOrder("A Movie", "An Album");
    }

    @Test
    void doesNotMergeHomonymsWhenWikidataIdentitiesDiffer() {
        Media movie = media(MediaType.MOVIE, "A Movie");
        Media album = media(MediaType.ALBUM, "An Album");

        mediaCreditService.save(movie, List.of(credit(
                "different-tmdb", "Same Name", CreditRole.COMPOSER, 0, ExternalSource.TMDB)));
        mediaCreditService.save(album, List.of(credit(
                "different-mb", "Same Name", CreditRole.ARTIST, 0, ExternalSource.MUSICBRAINZ)));
        entityManager.flush();

        assertThat(personRepository.count()).isEqualTo(2);
        assertThat(personExternalReferenceRepository
                .findBySourceAndExternalId(ExternalSource.WIKIDATA, "Q111")).isPresent();
        assertThat(personExternalReferenceRepository
                .findBySourceAndExternalId(ExternalSource.WIKIDATA, "Q222")).isPresent();
    }

    @Test
    void doesNotMergeHomonymsFromTheSameSourceWithDifferentIds() {
        Media firstMovie = media(MediaType.MOVIE, "First Movie");
        Media secondMovie = media(MediaType.MOVIE, "Second Movie");

        mediaCreditService.save(firstMovie, List.of(credit(
                "tmdb-homonym-1", "Same Name", CreditRole.ACTOR, 0, ExternalSource.TMDB)));
        mediaCreditService.save(secondMovie, List.of(credit(
                "tmdb-homonym-2", "Same Name", CreditRole.ACTOR, 0, ExternalSource.TMDB)));
        entityManager.flush();

        assertThat(personRepository.count()).isEqualTo(2);
        assertThat(personExternalReferenceRepository
                .findBySourceAndExternalId(ExternalSource.TMDB, "tmdb-homonym-1")).isPresent();
        assertThat(personExternalReferenceRepository
                .findBySourceAndExternalId(ExternalSource.TMDB, "tmdb-homonym-2")).isPresent();
    }

    @Test
    void reconcilesAndMergesLegacyCreditsOnAnExistingImport() {
        Media movie = media(MediaType.MOVIE, "Legacy Movie");
        Media album = media(MediaType.ALBUM, "Legacy Album");
        Person tmdbPerson = person("Legacy Artist", ExternalSource.TMDB, "legacy-tmdb");
        Person musicBrainzPerson = person("Legacy Artist", ExternalSource.MUSICBRAINZ, "legacy-mb");
        persistCredit(movie, tmdbPerson, CreditRole.COMPOSER);
        persistCredit(album, musicBrainzPerson, CreditRole.ARTIST);
        entityManager.flush();

        mediaCreditService.reconcile(album);
        entityManager.flush();
        entityManager.clear();

        assertThat(personRepository.count()).isEqualTo(1);
        var wikidataReference = personExternalReferenceRepository
                .findBySourceAndExternalId(ExternalSource.WIKIDATA, "Q999")
                .orElseThrow();
        assertThat(artistService.findDetails(wikidataReference.getPerson().getId()).workCount())
                .isEqualTo(2);
    }

    private Person person(String name, ExternalSource source, String externalId) {
        Person person = new Person();
        person.setName(name);
        person.setExternalSource(source);
        person.setExternalId(externalId);
        entityManager.persist(person);
        return person;
    }

    private void persistCredit(Media media, Person person, CreditRole role) {
        MediaCredit credit = new MediaCredit();
        credit.setMedia(media);
        credit.setPerson(person);
        credit.setRole(role);
        credit.setPosition(0);
        entityManager.persist(credit);
    }

    private Media media(MediaType type, String title) {
        Media media = new Media();
        media.setType(type);
        media.setTitle(title);
        entityManager.persist(media);
        return media;
    }

    private ExternalMedia.ExternalCredit credit(
            String externalId,
            String name,
            CreditRole role,
            int position
    ) {
        return credit(externalId, name, role, position, ExternalSource.TMDB);
    }

    private ExternalMedia.ExternalCredit credit(
            String externalId,
            String name,
            CreditRole role,
            int position,
            ExternalSource source
    ) {
        return new ExternalMedia.ExternalCredit(
                externalId,
                name,
                role,
                null,
                position,
                "https://image.tmdb.org/t/p/w500/profile.jpg",
                source
        );
    }
}
