package com.scriptles.cabinet.media.repository;

import com.scriptles.cabinet.lists.entity.MediaList;
import com.scriptles.cabinet.lists.entity.MediaListItem;
import com.scriptles.cabinet.lists.entity.MediaListLike;
import com.scriptles.cabinet.lists.repository.MediaListItemRepository;
import com.scriptles.cabinet.lists.repository.MediaListRepository;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.MediaLike;
import com.scriptles.cabinet.media.entity.Rating;
import com.scriptles.cabinet.media.entity.Review;
import com.scriptles.cabinet.media.entity.ReviewLike;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.entity.UserMedia;
import com.scriptles.cabinet.user.entity.UserMediaArtworkPreference;
import com.scriptles.cabinet.user.enums.AccountTier;
import com.scriptles.cabinet.user.enums.UserMediaStatus;
import com.scriptles.cabinet.user.enums.Visibility;
import com.scriptles.cabinet.user.repository.UserMediaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class MediaCommunityStatsRepositoryTest {
    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private RatingRepository ratingRepository;

    @Autowired
    private MediaLikeRepository mediaLikeRepository;

    @Autowired
    private MediaListItemRepository mediaListItemRepository;

    @Autowired
    private MediaListRepository mediaListRepository;

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

        var ratings = ratingRepository.summarizeRatings(
                List.of(media.getId()),
                Visibility.PUBLIC
        );

        assertThat(ratings).hasSize(1);
        assertThat(ratings.getFirst().getAverageRating()).isEqualTo(4.0);
        assertThat(ratingRepository.ratingDistribution(media.getId(), Visibility.PUBLIC))
                .extracting(
                        RatingRepository.RatingDistributionProjection::getRating,
                        RatingRepository.RatingDistributionProjection::getRatingCount
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
    void returnsTheThreeMostRecentPublicLikersAndCompleters() {
        Media media = media();
        User ana = user("recent-ana");
        User bia = user("recent-bia");
        User caio = user("recent-caio");
        User dora = user("recent-dora");
        User privateUser = user("recent-private");
        Instant base = Instant.parse("2026-07-20T12:00:00Z");

        MediaLike oldestLike = mediaLike(media, ana);
        MediaLike secondLike = mediaLike(media, bia);
        MediaLike thirdLike = mediaLike(media, caio);
        MediaLike newestLike = mediaLike(media, dora);
        UserMedia oldestCompletion = userMedia(
                media, ana, false, false, UserMediaStatus.COMPLETED);
        UserMedia secondCompletion = userMedia(
                media, bia, false, false, UserMediaStatus.COMPLETED);
        UserMedia thirdCompletion = userMedia(
                media, caio, false, false, UserMediaStatus.COMPLETED);
        UserMedia newestCompletion = userMedia(
                media, dora, false, false, UserMediaStatus.COMPLETED);
        UserMedia privateCompletion = userMedia(
                media, privateUser, false, true, UserMediaStatus.COMPLETED);

        entityManager.flush();
        oldestLike.setCreatedAt(base);
        secondLike.setCreatedAt(base.plusSeconds(1));
        thirdLike.setCreatedAt(base.plusSeconds(2));
        newestLike.setCreatedAt(base.plusSeconds(3));
        oldestLike.setLikedAt(base);
        secondLike.setLikedAt(base.plusSeconds(1));
        thirdLike.setLikedAt(base.plusSeconds(2));
        newestLike.setLikedAt(base.plusSeconds(3));
        oldestCompletion.setCompletedAt(base);
        secondCompletion.setCompletedAt(base.plusSeconds(1));
        thirdCompletion.setCompletedAt(base.plusSeconds(2));
        newestCompletion.setCompletedAt(base.plusSeconds(3));
        privateCompletion.setCompletedAt(base.plusSeconds(4));
        entityManager.flush();
        entityManager.clear();

        assertThat(mediaLikeRepository.findTop3ByMediaIdOrderByLikedAtDescIdDesc(media.getId()))
                .extracting(like -> like.getUser().getUsername())
                .containsExactly("recent-dora", "recent-caio", "recent-bia");
        assertThat(userMediaRepository
                .findTop3ByMediaIdAndStatusAndPrivateEntryFalseAndCompletedAtIsNotNullOrderByCompletedAtDescIdDesc(
                        media.getId(), UserMediaStatus.COMPLETED))
                .extracting(entry -> entry.getUser().getUsername())
                .containsExactly("recent-dora", "recent-caio", "recent-bia");
    }

    @Test
    void ranksPublicRatingsAndFindsOnlyRecentPublicActivity() {
        Instant since = Instant.now().minusSeconds(3600);
        Media highestAverage = media("Uma nota máxima", null);
        Media moreRatings = media("Duas notas", null);
        User ana = user("rank-ana");
        User bia = user("rank-bia");
        User caio = user("rank-caio");

        review(highestAverage, ana, "5.0", Visibility.PUBLIC);
        review(moreRatings, bia, "4.5", Visibility.PUBLIC);
        review(moreRatings, caio, "4.5", Visibility.PUBLIC);
        review(highestAverage, caio, "0.5", Visibility.PRIVATE);
        mediaLike(moreRatings, ana);
        userMedia(highestAverage, bia, false, false, UserMediaStatus.PLANNED);
        userMedia(moreRatings, ana, false, true, UserMediaStatus.PLANNED);

        entityManager.flush();
        entityManager.clear();

        Set<String> types = Set.of(MediaType.MOVIE.name());
        var ranking = ratingRepository.findTopRatedMedia(
                types, Visibility.PUBLIC, PageRequest.of(0, 20));
        assertThat(ranking.getContent())
                .extracting(item -> item.getMedia().getTitle())
                .containsExactly("Uma nota máxima", "Duas notas");
        assertThat(ranking.getContent())
                .extracting(RatingRepository.RatedMediaProjection::getRatingCount)
                .containsExactly(1L, 2L);

        assertThat(ratingRepository.findRecentActivity(
                types, Visibility.PUBLIC, since, PageRequest.of(0, 20)))
                .extracting(
                        RatingRepository.MediaActivityProjection::getMediaId,
                        RatingRepository.MediaActivityProjection::getActivityCount
                )
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(highestAverage.getId(), 1L),
                        org.assertj.core.groups.Tuple.tuple(moreRatings.getId(), 2L)
                );
        assertThat(mediaLikeRepository.findRecentActivity(types, since, PageRequest.of(0, 20)))
                .extracting(MediaLikeRepository.MediaActivityProjection::getMediaId)
                .containsExactly(moreRatings.getId());
        assertThat(userMediaRepository.findRecentPublicActivity(types, since, PageRequest.of(0, 20)))
                .extracting(UserMediaRepository.MediaActivityProjection::getMediaId)
                .containsExactly(highestAverage.getId());
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
    void ranksGlobalPopularReviewsAndListsWhileExcludingPrivateContent() {
        User ana = user("global-ana");
        User bia = user("global-bia");
        User caio = user("global-caio");
        Review firstReview = review(
                media("Primeira review", null), ana, "5.0", Visibility.PUBLIC);
        firstReview.setContent("Texto mais curtido");
        Review secondReview = review(
                media("Segunda review", null), bia, "4.5", Visibility.PUBLIC);
        secondReview.setContent("Outro texto");
        Review privateReview = review(
                media("Review privada", null), caio, "5.0", Visibility.PRIVATE);
        privateReview.setContent("Não deve aparecer");
        review(media("Só nota", null), caio, "4.0", Visibility.PUBLIC);
        reviewLike(firstReview, ana);
        reviewLike(firstReview, bia);
        reviewLike(secondReview, caio);
        reviewLike(privateReview, ana);

        MediaList firstList = mediaList(ana, "Lista mais curtida", Visibility.PUBLIC);
        MediaList secondList = mediaList(bia, "Outra lista", Visibility.PUBLIC);
        MediaList privateList = mediaList(caio, "Lista privada", Visibility.PRIVATE);
        listLike(firstList, ana);
        listLike(firstList, bia);
        listLike(secondList, caio);
        listLike(privateList, ana);

        entityManager.flush();
        entityManager.clear();

        assertThat(reviewRepository.findGloballyPopularIds(
                Visibility.PUBLIC, PageRequest.of(0, 20)))
                .containsExactly(firstReview.getId(), secondReview.getId());
        assertThat(mediaListRepository.findPopularPublicLists(
                Visibility.PUBLIC, PageRequest.of(0, 20)))
                .extracting(MediaListRepository.PopularListProjection::getListId)
                .containsExactly(firstList.getId(), secondList.getId());
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

    @Test
    void includesCoverlessMediaWhenProListOwnerSelectedACustomCover() {
        User owner = user("pro-cover-owner");
        owner.setAccountTier(AccountTier.PRO);
        Media media = media("Sem capa canônica", null);
        MediaList list = mediaList(owner, "Personalizada", Visibility.PUBLIC);
        listItem(media, list, 1);

        UserMediaArtworkPreference preference = new UserMediaArtworkPreference();
        preference.setUser(owner);
        preference.setMedia(media);
        preference.setCoverUrl("https://example.com/custom.jpg");
        entityManager.persist(preference);
        entityManager.flush();
        entityManager.clear();

        var covers = mediaListItemRepository.findRecentCoversByListIds(
                List.of(list.getId()));

        assertThat(covers)
                .extracting(MediaListItemRepository.MediaListCover::getMediaId)
                .containsExactly(media.getId());
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

    private UserMedia userMedia(
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
        return entityManager.persist(entry);
    }

    private MediaLike mediaLike(Media media, User user) {
        MediaLike like = new MediaLike();
        like.setMedia(media);
        like.setUser(user);
        return entityManager.persist(like);
    }

    private Review review(Media media, User user, String rating, Visibility visibility) {
        Rating ratingEntity = new Rating();
        ratingEntity.setMedia(media);
        ratingEntity.setUser(user);
        ratingEntity.setValue(new BigDecimal(rating));
        ratingEntity.setVisibility(visibility);
        entityManager.persist(ratingEntity);

        Review review = new Review();
        review.setMedia(media);
        review.setUser(user);
        review.setRatingEntity(ratingEntity);
        review.setVisibility(visibility);
        return entityManager.persist(review);
    }

    private void reviewLike(Review review, User user) {
        ReviewLike like = new ReviewLike();
        like.setReview(review);
        like.setUser(user);
        entityManager.persist(like);
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
