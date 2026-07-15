package com.scriptles.cabinet.media.repository;

import com.scriptles.cabinet.media.entity.BookDetails;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.MediaType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class BookDetailsRepositoryTest {
    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private BookDetailsRepository bookDetailsRepository;

    @Test
    void keepsEditionsSeparateWhileSharingTheCanonicalWork() {
        book("Edição brasileira", "9788535914849", "Q166442");
        book("English edition", "9780140283334", "Q166442");
        entityManager.flush();
        entityManager.clear();

        var editions = bookDetailsRepository.findAllByCanonicalWorkWikidataIdIn(Set.of("Q166442"));

        assertThat(editions).hasSize(2);
        assertThat(editions).extracting(BookDetails::getIsbn13)
                .containsExactlyInAnyOrder("9788535914849", "9780140283334");
    }

    private void book(String title, String isbn13, String canonicalWorkWikidataId) {
        Media media = new Media();
        media.setType(MediaType.BOOK);
        media.setTitle(title);
        entityManager.persist(media);

        BookDetails details = new BookDetails();
        details.setMedia(media);
        details.setIsbn13(isbn13);
        details.setCanonicalWorkWikidataId(canonicalWorkWikidataId);
        entityManager.persist(details);
    }
}
