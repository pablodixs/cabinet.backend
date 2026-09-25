package com.scriptles.cabinet.media.repository;

import com.scriptles.cabinet.media.entity.AlbumTrack;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.MediaType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class AlbumTrackCursorRepositoryTest {
    @Autowired private TestEntityManager entityManager;
    @Autowired private AlbumTrackRepository repository;

    @Test
    void keysetPagesKeepStableOrderingAcrossDuplicateAndNullPositions() {
        Media album = media("Album", MediaType.ALBUM);
        List<AlbumTrack> expected = new ArrayList<>();
        expected.add(addTrack(album, "First", 1, 1));
        expected.add(addTrack(album, "Duplicate A", 1, 2));
        expected.add(addTrack(album, "Duplicate B", 1, 2));
        expected.add(addTrack(album, "Unnumbered", null, null));
        entityManager.flush();
        expected.sort(Comparator
                .comparing(AlbumTrack::getDiscNumber, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(AlbumTrack::getTrackNumber, Comparator.nullsLast(Integer::compareTo))
                // PostgreSQL/H2 UUID ordering is lexicographic; Java UUID.compareTo
                // compares signed halves and can disagree for generated UUIDs.
                .thenComparing(track -> track.getId().toString()));
        entityManager.clear();

        List<String> titles = new ArrayList<>();
        boolean firstPage = true;
        Integer cursorDisc = null;
        Integer cursorTrack = null;
        UUID cursorId = new UUID(0, 0);
        List<AlbumTrack> page;
        do {
            page = repository.findAlbumPageAfter(album.getId(), firstPage,
                    cursorDisc == null, cursorDisc, cursorTrack == null, cursorTrack,
                    cursorId, PageRequest.of(0, 2));
            titles.addAll(page.stream().map(AlbumTrack::getTitle).toList());
            if (!page.isEmpty()) {
                AlbumTrack last = page.get(page.size() - 1);
                firstPage = false;
                cursorDisc = last.getDiscNumber();
                cursorTrack = last.getTrackNumber();
                cursorId = last.getId();
            }
        } while (page.size() == 2);

        assertThat(titles).containsExactlyElementsOf(expected.stream().map(AlbumTrack::getTitle).toList());
    }

    private Media media(String title, MediaType type) {
        Media media = new Media();
        media.setType(type);
        media.setTitle(title);
        return entityManager.persist(media);
    }

    private AlbumTrack addTrack(Media album, String title, Integer disc, Integer trackNumber) {
        Media trackMedia = media(title, MediaType.TRACK);
        AlbumTrack track = new AlbumTrack();
        track.setAlbum(album);
        track.setTrackMedia(trackMedia);
        track.setTitle(title);
        track.setDiscNumber(disc);
        track.setTrackNumber(trackNumber);
        return entityManager.persist(track);
    }
}
