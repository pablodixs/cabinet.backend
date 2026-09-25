package com.scriptles.cabinet.media.repository;

import com.scriptles.cabinet.media.entity.AlbumReleaseVersion;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.MediaType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class AlbumReleaseVersionCursorRepositoryTest {
    @Autowired private TestEntityManager entityManager;
    @Autowired private AlbumReleaseVersionRepository repository;

    @Test
    void fetchesInitialAndSubsequentCursorPages() {
        Media album = new Media();
        album.setType(MediaType.ALBUM);
        album.setTitle("Album");
        entityManager.persist(album);

        for (int index = 0; index < 3; index++) {
            AlbumReleaseVersion version = new AlbumReleaseVersion();
            version.setAlbum(album);
            version.setMusicBrainzReleaseId(UUID.randomUUID());
            version.setTitle("Version " + index);
            entityManager.persist(version);
        }
        entityManager.flush();
        entityManager.clear();

        List<AlbumReleaseVersion> firstPage = repository.findByAlbumIdOrderByIdAsc(
                album.getId(), PageRequest.of(0, 2));
        List<AlbumReleaseVersion> secondPage = repository.findPageForAlbumAfter(
                album.getId(), firstPage.getLast().getId(), PageRequest.of(0, 2));

        List<UUID> ids = new ArrayList<>();
        ids.addAll(firstPage.stream().map(AlbumReleaseVersion::getId).toList());
        ids.addAll(secondPage.stream().map(AlbumReleaseVersion::getId).toList());

        assertThat(firstPage).hasSize(2);
        assertThat(secondPage).hasSize(1);
        assertThat(ids).doesNotHaveDuplicates();
        assertThat(repository.findByAlbumIdOrderByIdAsc(album.getId(), PageRequest.of(0, 3)))
                .extracting(AlbumReleaseVersion::getId)
                .containsExactlyElementsOf(ids);
    }
}
