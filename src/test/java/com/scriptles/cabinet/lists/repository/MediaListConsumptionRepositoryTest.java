package com.scriptles.cabinet.lists.repository;

import com.scriptles.cabinet.lists.entity.MediaList;
import com.scriptles.cabinet.lists.entity.MediaListItem;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.entity.UserMedia;
import com.scriptles.cabinet.user.enums.UserMediaStatus;
import com.scriptles.cabinet.user.repository.UserMediaRepository;
import com.scriptles.cabinet.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class MediaListConsumptionRepositoryTest {
    @Autowired MediaListItemRepository itemRepository;
    @Autowired MediaListRepository listRepository;
    @Autowired MediaRepository mediaRepository;
    @Autowired UserMediaRepository userMediaRepository;
    @Autowired UserRepository userRepository;

    @Test
    void countsOnlyCompletedItemsForTheSelectedViewer() {
        User owner = userRepository.save(User.create(
                "list-owner@example.com", "list-owner", "List Owner", "hash"));
        User viewer = userRepository.save(User.create(
                "list-viewer@example.com", "list-viewer", "List Viewer", "hash"));
        MediaList list = new MediaList();
        list.setOwner(owner);
        list.setName("Essenciais");
        MediaList savedList = listRepository.save(list);

        Media completedMovie = media("Completed movie");
        Media completedBook = media("Completed book");
        Media inProgressAlbum = media("In progress album");
        addItem(savedList, completedMovie, 1);
        addItem(savedList, completedBook, 2);
        addItem(savedList, inProgressAlbum, 3);
        addLibraryEntry(viewer, completedMovie, UserMediaStatus.COMPLETED);
        addLibraryEntry(viewer, completedBook, UserMediaStatus.COMPLETED);
        addLibraryEntry(viewer, inProgressAlbum, UserMediaStatus.IN_PROGRESS);
        itemRepository.flush();

        var counts = itemRepository.countConsumedByListIds(
                viewer.getId(), List.of(savedList.getId()), UserMediaStatus.COMPLETED);

        assertThat(counts).singleElement().satisfies(count -> {
            assertThat(count.getListId()).isEqualTo(savedList.getId());
            assertThat(count.getConsumedItemCount()).isEqualTo(2);
        });
    }

    private Media media(String title) {
        Media media = new Media();
        media.setType(MediaType.MOVIE);
        media.setTitle(title);
        return mediaRepository.save(media);
    }

    private void addItem(MediaList list, Media media, int position) {
        MediaListItem item = new MediaListItem();
        item.setList(list);
        item.setMedia(media);
        item.setPosition(position);
        itemRepository.save(item);
    }

    private void addLibraryEntry(
            User viewer,
            Media media,
            UserMediaStatus status
    ) {
        UserMedia entry = new UserMedia();
        entry.setUser(viewer);
        entry.setMedia(media);
        entry.setStatus(status);
        entry.setFavorite(false);
        entry.setPrivateEntry(false);
        userMediaRepository.save(entry);
    }
}
