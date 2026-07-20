package com.scriptles.cabinet.media.repository;

import com.scriptles.cabinet.media.entity.AwardEntry;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.Person;
import com.scriptles.cabinet.media.enums.AwardOrigin;
import com.scriptles.cabinet.media.enums.AwardResult;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class AwardEntryRepositoryTest {
    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private AwardEntryRepository repository;

    @Test
    void persistsAnAwardLinkedToExactlyOneMedia() {
        Media media = new Media();
        media.setType(MediaType.MOVIE);
        media.setTitle("Central do Brasil");
        entityManager.persist(media);

        AwardEntry entry = award();
        entry.setMedia(media);
        repository.saveAndFlush(entry);

        assertThat(entry.getId()).isNotNull();
        assertThat(repository.findAllByMediaId(media.getId())).containsExactly(entry);
    }

    @Test
    void rejectsAnAwardWithoutMediaOrPerson() {
        repository.save(award());

        assertThatThrownBy(repository::flush)
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsAnAwardLinkedToMediaAndPersonAtTheSameTime() {
        Media media = new Media();
        media.setType(MediaType.BOOK);
        media.setTitle("Torto Arado");
        entityManager.persist(media);
        Person person = new Person();
        person.setName("Itamar Vieira Junior");
        person.setExternalSource(ExternalSource.MANUAL);
        entityManager.persist(person);

        AwardEntry entry = award();
        entry.setMedia(media);
        entry.setPerson(person);
        repository.save(entry);

        assertThatThrownBy(repository::flush)
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void ordersDatedAwardsFirstAndKeepsFilteredTotalsIndependent() {
        Media media = new Media();
        media.setType(MediaType.MOVIE);
        media.setTitle("O Agente Secreto");
        entityManager.persist(media);
        AwardEntry olderWin = award(media, AwardResult.WIN, "Prêmio antigo", 2020,
                LocalDate.of(2020, 2, 9));
        AwardEntry newerNomination = award(media, AwardResult.NOMINATION, "Prêmio recente", 2025,
                LocalDate.of(2025, 5, 24));
        AwardEntry undatedWin = award(media, AwardResult.WIN, "Prêmio sem data", null, null);
        repository.saveAllAndFlush(java.util.List.of(undatedWin, olderWin, newerNomination));

        assertThat(repository.findVisibleByMediaId(
                media.getId(), null, PageRequest.of(0, 10)).getContent())
                .extracting(AwardEntry::getCategoryName)
                .containsExactly("Prêmio recente", "Prêmio antigo", "Prêmio sem data");
        assertThat(repository.findVisibleByMediaId(
                media.getId(), AwardResult.WIN, PageRequest.of(0, 10)).getContent())
                .extracting(AwardEntry::getCategoryName)
                .containsExactly("Prêmio antigo", "Prêmio sem data");
        assertThat(repository.countByMediaIdAndHiddenFalseAndResult(media.getId(), AwardResult.WIN))
                .isEqualTo(2);
        assertThat(repository.countByMediaIdAndHiddenFalseAndResult(
                media.getId(), AwardResult.NOMINATION)).isEqualTo(1);
    }

    private AwardEntry award() {
        AwardEntry entry = new AwardEntry();
        entry.setResult(AwardResult.WIN);
        entry.setCategoryName("Melhor obra");
        entry.setOrigin(AwardOrigin.MANUAL);
        return entry;
    }

    private AwardEntry award(
            Media media,
            AwardResult result,
            String category,
            Integer year,
            LocalDate date
    ) {
        AwardEntry entry = award();
        entry.setMedia(media);
        entry.setResult(result);
        entry.setCategoryName(category);
        entry.setEventYear(year);
        entry.setEventDate(date);
        return entry;
    }
}
