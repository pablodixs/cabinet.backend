package com.scriptles.cabinet.user.repository;

import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.entity.UserInterestPreference;
import com.scriptles.cabinet.user.enums.InterestPreference;
import com.scriptles.cabinet.user.enums.InterestTargetType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.PageRequest;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect")
class UserInterestRepositoryTest {
    @Autowired TestEntityManager entityManager;
    @Autowired MediaRepository mediaRepository;
    @Autowired UserInterestPreferenceRepository preferenceRepository;

    @Test
    void findsCandidatesAndGenreLabelsByNormalizedGenre() {
        Media drama = media("Drama", "Drama");
        media("Comedy", "Comedy");
        entityManager.flush();
        entityManager.clear();

        List<Media> candidates = mediaRepository.findInterestCandidates(
                Set.of("MOVIE"), List.of(new UUID(0, 0)), List.of("drama"),
                List.of(new UUID(0, 0)), PageRequest.of(0, 20));

        assertThat(candidates).extracting(Media::getId).containsExactly(drama.getId());
        assertThat(mediaRepository.findGenreLabels("drama", PageRequest.of(0, 1)))
                .containsExactly("Drama");
    }

    @Test
    void storesAndLoadsAnOwnedExplicitPreference() {
        User user = entityManager.persist(User.create(
                "reader@cabinet.test", "reader", "Reader", "hash"));
        UserInterestPreference preference = new UserInterestPreference();
        preference.setUser(user);
        preference.setTargetType(InterestTargetType.GENRE);
        preference.setGenreKey("science fiction");
        preference.setGenreLabel("Science Fiction");
        preference.setPreference(InterestPreference.POSITIVE);
        entityManager.persist(preference);
        entityManager.flush();
        entityManager.clear();

        assertThat(preferenceRepository.findAllByUserId(user.getId()))
                .singleElement()
                .satisfies(saved -> {
                    assertThat(saved.getGenreKey()).isEqualTo("science fiction");
                    assertThat(saved.getPreference()).isEqualTo(InterestPreference.POSITIVE);
                });
    }

    private Media media(String title, String genre) {
        Media media = new Media();
        media.setType(MediaType.MOVIE);
        media.setTitle(title);
        media.setGenres(new LinkedHashSet<>(Set.of(genre)));
        return entityManager.persist(media);
    }
}
