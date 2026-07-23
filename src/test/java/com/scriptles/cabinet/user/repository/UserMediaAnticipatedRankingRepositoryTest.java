package com.scriptles.cabinet.user.repository;

import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.entity.UserMedia;
import com.scriptles.cabinet.user.enums.UserMediaStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

@DataJpaTest
class UserMediaAnticipatedRankingRepositoryTest {
    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserMediaRepository userMediaRepository;

    @Test
    void ranksOnlyFutureMoviesByPublicPlannedEntries() {
        LocalDate today = LocalDate.of(2026, 7, 23);
        Media mostPlanned = media("Mais aguardado", MediaType.MOVIE, today.plusMonths(2));
        Media nextRelease = media("Desempate por lançamento", MediaType.MOVIE, today.plusMonths(1));
        Media laterRelease = media("Desempate posterior", MediaType.MOVIE, today.plusMonths(3));
        Media released = media("Já lançado", MediaType.MOVIE, today);
        Media futureSeries = media("Série futura", MediaType.SERIES, today.plusMonths(1));

        planned(mostPlanned, user("ana"), false);
        planned(mostPlanned, user("bia"), false);
        planned(mostPlanned, user("caio"), true);
        planned(nextRelease, user("dora"), false);
        planned(laterRelease, user("eva"), false);
        planned(released, user("fabi"), false);
        planned(futureSeries, user("gabi"), false);

        entityManager.flush();
        entityManager.clear();

        var result = userMediaRepository.findMostAnticipatedMovies(
                UserMediaStatus.PLANNED,
                today,
                PageRequest.of(0, 10)
        );

        assertThat(result)
                .extracting(
                        item -> item.getMedia().getTitle(),
                        UserMediaRepository.AnticipatedMediaProjection::getPlannedCount
                )
                .containsExactly(
                        tuple("Mais aguardado", 2L),
                        tuple("Desempate por lançamento", 1L),
                        tuple("Desempate posterior", 1L)
                );
    }

    private Media media(String title, MediaType type, LocalDate releaseDate) {
        Media media = new Media();
        media.setTitle(title);
        media.setType(type);
        media.setReleaseDate(releaseDate);
        return entityManager.persist(media);
    }

    private User user(String username) {
        return entityManager.persist(User.create(
                username + "@cabinet.test",
                username,
                username,
                "hash"
        ));
    }

    private void planned(Media media, User user, boolean privateEntry) {
        UserMedia entry = new UserMedia();
        entry.setMedia(media);
        entry.setUser(user);
        entry.setStatus(UserMediaStatus.PLANNED);
        entry.setPrivateEntry(privateEntry);
        entityManager.persist(entry);
    }
}
