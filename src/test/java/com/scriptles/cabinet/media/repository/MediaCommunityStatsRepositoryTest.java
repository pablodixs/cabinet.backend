package com.scriptles.cabinet.media.repository;

import com.scriptles.cabinet.lists.entity.MediaList;
import com.scriptles.cabinet.lists.entity.MediaListItem;
import com.scriptles.cabinet.lists.entity.MediaListLike;
import com.scriptles.cabinet.lists.repository.MediaListItemRepository;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.MediaLike;
import com.scriptles.cabinet.media.entity.Review;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.entity.UserMedia;
import com.scriptles.cabinet.user.enums.UserMediaStatus;
import com.scriptles.cabinet.user.enums.Visibility;
import com.scriptles.cabinet.user.repository.UserMediaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class MediaCommunityStatsRepositoryTest {
    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private MediaLikeRepository mediaLikeRepository;

    @Autowired
    private MediaListItemRepository mediaListItemRepository;

    @Autowired
    private UserMediaRepository userMediaRepository;

    @Test
    void aggregatesOnlyPublicCommunityActivity() {
        Media media = media();
        User ana = user("ana");
        User bia = user("bia");
        User caio = user("caio");

        mediaLike(media, ana);
        userMedia(media, ana, false, false, UserMediaStatus.COMPLETED);
        userMedia(media, bia, false, false, UserMediaStatus.COMPLETED);
        userMedia(media, caio, true, true, UserMediaStatus.COMPLETED);

        review(media, ana, "5.0", Visibility.PUBLIC);
        review(media, bia, "3.0", Visibility.PUBLIC);
        review(media, caio, "1.0", Visibility.PRIVATE);

        listItem(media, mediaList(ana, "Pública", Visibility.PUBLIC));
        listItem(media, mediaList(bia, "Seguidores", Visibility.FOLLOWERS));
        listItem(media, mediaList(caio, "Privada", Visibility.PRIVATE));

        entityManager.flush();
        entityManager.clear();

        var ratings = reviewRepository.summarizeRatings(
                List.of(media.getId()),
                Visibility.PUBLIC
        );

        assertThat(ratings).hasSize(1);
        assertThat(ratings.getFirst().getAverageRating()).isEqualTo(4.0);
        assertThat(reviewRepository.ratingDistribution(media.getId(), Visibility.PUBLIC))
                .extracting(
                        ReviewRepository.RatingDistributionProjection::getRating,
                        ReviewRepository.RatingDistributionProjection::getRatingCount
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(new BigDecimal("3.0"), 1L),
                        org.assertj.core.groups.Tuple.tuple(new BigDecimal("5.0"), 1L)
                );
        assertThat(mediaLikeRepository.countByMediaId(media.getId())).isEqualTo(1);
        assertThat(userMediaRepository.countByMediaIdAndStatusAndPrivateEntryFalse(
                media.getId(), UserMediaStatus.COMPLETED)).isEqualTo(2);
        assertThat(mediaListItemRepository.countByMediaIdAndListVisibility(
                media.getId(), Visibility.PUBLIC)).isEqualTo(1);

        var publicLists = mediaListItemRepository.findAllByMediaIdAndListVisibility(
                media.getId(),
                Visibility.PUBLIC,
                PageRequest.of(0, 20)
        );
        assertThat(publicLists.getContent())
                .extracting(item -> item.getItem().getList().getName())
                .containsExactly("Pública");
    }

    @Test
    void ranksPublicListsByLikeCount() {
        Media media = media();
        User ana = user("ana-ranking");
        User bia = user("bia-ranking");
        User caio = user("caio-ranking");
        MediaList first = mediaList(ana, "Mais curtida", Visibility.PUBLIC);
        MediaList second = mediaList(bia, "Menos curtida", Visibility.PUBLIC);
        MediaList privateList = mediaList(caio, "Privada", Visibility.PRIVATE);

        listItem(media, second);
        listItem(media, privateList);
        listItem(media, first);
        listLike(first, ana);
        listLike(first, bia);
        listLike(second, caio);
        listLike(privateList, ana);

        entityManager.flush();
        entityManager.clear();

        var lists = mediaListItemRepository.findAllByMediaIdAndListVisibility(
                media.getId(),
                Visibility.PUBLIC,
                PageRequest.of(0, 20)
        );

        assertThat(lists.getContent())
                .extracting(item -> item.getItem().getList().getName())
                .containsExactly("Mais curtida", "Menos curtida");
        assertThat(lists.getContent())
                .extracting(MediaListItemRepository.MediaListPopularity::getLikeCount)
                .containsExactly(2L, 1L);
    }

    @Test
    void returnsOnlyTheFourMostRecentlyAddedAvailableCoversPerList() {
        User owner = user("cover-owner");
        MediaList firstList = mediaList(owner, "Primeira", Visibility.PUBLIC);
        MediaList secondList = mediaList(owner, "Segunda", Visibility.PUBLIC);

        for (int position = 1; position <= 5; position++) {
            Media media = media("Obra " + position, "https://example.com/" + position + ".jpg");
            listItem(media, firstList, position);
        }
        listItem(media("Sem capa", null), firstList, 6);
        listItem(media("Outra lista", "https://example.com/other.jpg"), secondList, 1);

        entityManager.flush();
        entityManager.clear();

        var covers = mediaListItemRepository.findRecentCoversByListIds(
                List.of(firstList.getId(), secondList.getId())
        );

        assertThat(covers).filteredOn(cover -> cover.getListId().equals(firstList.getId()))
                .extracting(MediaListItemRepository.MediaListCover::getCoverUrl)
                .containsExactly(
                        "https://example.com/5.jpg",
                        "https://example.com/4.jpg",
                        "https://example.com/3.jpg",
                        "https://example.com/2.jpg"
                );
        assertThat(covers).filteredOn(cover -> cover.getListId().equals(firstList.getId()))
                .extracting(MediaListItemRepository.MediaListCover::getType)
                .containsOnly(MediaType.MOVIE);
        assertThat(covers).filteredOn(cover -> cover.getListId().equals(secondList.getId()))
                .extracting(MediaListItemRepository.MediaListCover::getCoverUrl)
                .containsExactly("https://example.com/other.jpg");
    }

    private Media media() {
        return media("Fight Club", null);
    }

    private Media media(String title, String coverUrl) {
        Media media = new Media();
        media.setType(MediaType.MOVIE);
        media.setTitle(title);
        media.setCoverUrl(coverUrl);
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

    private void userMedia(
            Media media,
            User user,
            boolean favorite,
            boolean privateEntry,
            UserMediaStatus status
    ) {
        UserMedia entry = new UserMedia();
        entry.setMedia(media);
        entry.setUser(user);
        entry.setFavorite(favorite);
        entry.setPrivateEntry(privateEntry);
        entry.setStatus(status);
        entityManager.persist(entry);
    }

    private void mediaLike(Media media, User user) {
        MediaLike like = new MediaLike();
        like.setMedia(media);
        like.setUser(user);
        entityManager.persist(like);
    }

    private void review(Media media, User user, String rating, Visibility visibility) {
        Review review = new Review();
        review.setMedia(media);
        review.setUser(user);
        review.setRating(new BigDecimal(rating));
        review.setVisibility(visibility);
        entityManager.persist(review);
    }

    private MediaList mediaList(User owner, String name, Visibility visibility) {
        MediaList list = new MediaList();
        list.setOwner(owner);
        list.setName(name);
        list.setVisibility(visibility);
        return entityManager.persist(list);
    }

    private void listItem(Media media, MediaList list) {
        listItem(media, list, 1);
    }

    private void listItem(Media media, MediaList list, int position) {
        MediaListItem item = new MediaListItem();
        item.setMedia(media);
        item.setList(list);
        item.setPosition(position);
        entityManager.persist(item);
    }

    private void listLike(MediaList list, User user) {
        MediaListLike like = new MediaListLike();
        like.setList(list);
        like.setUser(user);
        entityManager.persist(like);
    }
}
