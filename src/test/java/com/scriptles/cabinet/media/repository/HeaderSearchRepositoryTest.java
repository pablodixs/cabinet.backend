package com.scriptles.cabinet.media.repository;

import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.MediaCredit;
import com.scriptles.cabinet.media.entity.Person;
import com.scriptles.cabinet.media.enums.CreditRole;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class HeaderSearchRepositoryTest {
    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private MediaRepository mediaRepository;

    @Autowired
    private PersonRepository personRepository;

    @Test
    void ordersExactMediaTitleBeforePrefixAndPartialMatches() {
        Media partial = persistMedia("Animatrix", MediaType.MOVIE);
        Media prefix = persistMedia("Matrix Reloaded", MediaType.MOVIE);
        Media exact = persistMedia("Matrix", MediaType.MOVIE);
        persistMedia("Matrix", MediaType.BOOK);
        entityManager.flush();
        entityManager.clear();

        assertThat(mediaRepository.findHeaderSearchCandidates(
                "matrix", MediaType.MOVIE.name(), PageRequest.of(0, 5)))
                .extracting(Media::getId)
                .containsExactly(
                        exact.getId(),
                        prefix.getId(),
                        partial.getId()
                );
    }

    @Test
    void filtersArtistsByTheTypeOfTheirCredits() {
        Person movieArtist = persistPerson("Matrix Director");
        Person bookArtist = persistPerson("Matrix Author");
        addCredit(movieArtist, persistMedia("A Movie", MediaType.MOVIE), CreditRole.DIRECTOR);
        addCredit(bookArtist, persistMedia("A Book", MediaType.BOOK), CreditRole.AUTHOR);
        entityManager.flush();
        entityManager.clear();

        assertThat(personRepository.findHeaderSearchCandidates(
                "matrix", MediaType.MOVIE.name(), PageRequest.of(0, 5)))
                .extracting(Person::getId)
                .containsExactly(movieArtist.getId());
    }

    private Media persistMedia(String title, MediaType type) {
        Media media = new Media();
        media.setTitle(title);
        media.setType(type);
        return entityManager.persist(media);
    }

    private Person persistPerson(String name) {
        Person person = new Person();
        person.setName(name);
        person.setExternalSource(ExternalSource.MANUAL);
        return entityManager.persist(person);
    }

    private void addCredit(Person person, Media media, CreditRole role) {
        MediaCredit credit = new MediaCredit();
        credit.setPerson(person);
        credit.setMedia(media);
        credit.setRole(role);
        entityManager.persist(credit);
    }
}
