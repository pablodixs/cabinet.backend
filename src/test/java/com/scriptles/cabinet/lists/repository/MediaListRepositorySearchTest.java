package com.scriptles.cabinet.lists.repository;

import com.scriptles.cabinet.lists.entity.MediaList;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.enums.Visibility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect")
class MediaListRepositorySearchTest {
    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private MediaListRepository mediaListRepository;

    @Test
    void ranksNameMatchesAndExcludesNonPublicLists() {
        User owner = User.create(
                "maria@cabinet.test",
                "maria",
                "Maria",
                "hash"
        );
        entityManager.persist(owner);
        persistList(owner, "Cinema", null, Visibility.PUBLIC);
        persistList(owner, "Cinema de estrada", null, Visibility.PUBLIC);
        persistList(owner, "Favoritos", "Uma seleção de cinema brasileiro.", Visibility.PUBLIC);
        persistList(owner, "Cinema secreto", null, Visibility.PRIVATE);
        entityManager.flush();
        entityManager.clear();

        var results = mediaListRepository.searchPublicLists(
                "cinema",
                Visibility.PUBLIC,
                PageRequest.of(0, 20)
        );

        assertThat(results.getContent())
                .extracting(MediaList::getName)
                .containsExactly("Cinema", "Cinema de estrada", "Favoritos");
        assertThat(results.getContent().getFirst().getOwner().getUsername())
                .isEqualTo("maria");
    }

    private void persistList(
            User owner,
            String name,
            String description,
            Visibility visibility
    ) {
        MediaList list = new MediaList();
        list.setOwner(owner);
        list.setName(name);
        list.setDescription(description);
        list.setVisibility(visibility);
        list.setOrdered(true);
        entityManager.persist(list);
    }
}
