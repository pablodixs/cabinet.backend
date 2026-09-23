package com.scriptles.cabinet.media.repository;

import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.Rating;
import com.scriptles.cabinet.media.entity.Review;
import com.scriptles.cabinet.media.entity.ReviewLike;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.enums.Visibility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect")
class ReviewRepositorySearchTest {
    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private RatingRepository ratingRepository;

    @Autowired
    private ReviewLikeRepository reviewLikeRepository;

    @Test
    void ranksPublicReviewsAndExcludesPrivateOnlyMedia() {
        Media oneReview = media("Matrix A");
        Media twoReviews = media("Matrix B");
        Media privateOnly = media("Matrix C");
        reference(oneReview, "a");
        reference(twoReviews, "b");
        reference(privateOnly, "c");

        review(oneReview, user("ana"), "5.0", Visibility.PUBLIC);
        review(twoReviews, user("bia"), "5.0", Visibility.PUBLIC);
        review(twoReviews, user("caio"), "5.0", Visibility.PUBLIC);
        review(privateOnly, user("dani"), "5.0", Visibility.PRIVATE);
        entityManager.flush();
        entityManager.clear();

        var results = ratingRepository.searchRatedMedia(
                "matrix",
                Set.of(MediaType.MOVIE.name()),
                Visibility.PUBLIC,
                PageRequest.of(0, 20)
        );

        assertThat(results.getContent())
                .extracting(projection -> projection.getMedia().getTitle())
                .containsExactly("Matrix B", "Matrix A");
        assertThat(results.getContent().getFirst().getAverageRating()).isEqualTo(5.0);
        assertThat(results.getContent().getFirst().getRatingCount()).isEqualTo(2);
    }

    @Test
    void returnsOnlyTheTopThreePopularAndRecentPublicReviews() {
        Media media = media("The Matrix");
        Review oldest = review(media, user("eva"), "5.0", Visibility.PUBLIC);
        Review second = review(media, user("fabio"), "2.0", Visibility.PUBLIC);
        Review third = review(media, user("gabi"), "4.0", Visibility.PUBLIC);
        Review newest = review(media, user("hugo"), "3.0", Visibility.PUBLIC);
        review(media, user("iris"), "5.0", Visibility.PRIVATE);
        like(second, user("joao"));
        like(second, user("karina"));
        like(oldest, user("luana"));
        like(newest, user("marco"));
        like(newest, user("nina"));
        like(newest, user("otavio"));
        entityManager.flush();

        setCreatedAt(oldest, Instant.parse("2026-07-13T12:00:00Z"));
        setCreatedAt(second, Instant.parse("2026-07-14T12:00:00Z"));
        setCreatedAt(third, Instant.parse("2026-07-15T12:00:00Z"));
        setCreatedAt(newest, Instant.parse("2026-07-16T12:00:00Z"));
        entityManager.clear();

        List<UUID> popularIds = reviewRepository
                .findPopularIds(
                        media.getId(),
                        Visibility.PUBLIC,
                        PageRequest.of(0, 3)
                );
        Map<UUID, Review> reviewsById = reviewRepository
                .findAllByIdIn(popularIds)
                .stream()
                .collect(Collectors.toMap(Review::getId, review -> review));
        List<Review> popular = popularIds.stream().map(reviewsById::get).toList();
        List<Review> recent = reviewRepository
                .findTop3ByRatingMediaIdAndRatingVisibilityOrderByCreatedAtDescIdDesc(
                        media.getId(),
                        Visibility.PUBLIC
                );

        assertThat(popular).extracting(Review::getRating)
                .containsExactly(
                        new BigDecimal("3.0"),
                        new BigDecimal("2.0"),
                        new BigDecimal("5.0")
                );
        assertThat(recent).extracting(Review::getRating)
                .containsExactly(
                        new BigDecimal("3.0"),
                        new BigDecimal("4.0"),
                        new BigDecimal("2.0")
                );
    }

    @Test
    void hidesLegacyReviewsWithoutText() {
        Media media = media("Legacy review");
        Review legacy = review(media, user("legacy"), "4.0", Visibility.PUBLIC);
        legacy.setContent(null);
        entityManager.flush();
        entityManager.clear();

        assertThat(reviewRepository.findByIdAndVisibility(legacy.getId(), Visibility.PUBLIC))
                .isEmpty();
        assertThat(reviewRepository.findRecent(media.getId(), Visibility.PUBLIC, PageRequest.of(0, 3)))
                .isEmpty();
    }

    @Test
    void returnsOnlyTheFiveMostRecentLikersWithTheirProfiles() {
        Media media = media("Arrival");
        Review review = review(media, user("reviewer"), "4.5", Visibility.PUBLIC);
        List<ReviewLike> likes = java.util.stream.IntStream.range(0, 6)
                .mapToObj(index -> like(
                        review,
                        user("liker" + index, "https://example.com/liker" + index + ".jpg")
                ))
                .toList();
        entityManager.flush();

        for (int index = 0; index < likes.size(); index++) {
            setCreatedAt(likes.get(index), Instant.parse("2026-07-1" + index + "T12:00:00Z"));
        }
        entityManager.clear();

        List<ReviewLikeRepository.RecentReviewLiker> recentLikers =
                reviewLikeRepository.findRecentLikers(List.of(review.getId()));

        assertThat(recentLikers).hasSize(5);
        assertThat(recentLikers)
                .extracting(ReviewLikeRepository.RecentReviewLiker::getUsername)
                .containsExactly("liker5", "liker4", "liker3", "liker2", "liker1");
        assertThat(UUID.fromString(recentLikers.getFirst().getUserId()))
                .isEqualTo(likes.get(5).getUser().getId());
        assertThat(recentLikers.getFirst().getAvatarUrl())
                .isEqualTo("https://example.com/liker5.jpg");
    }

    private Media media(String title) {
        Media media = new Media();
        media.setType(MediaType.MOVIE);
        media.setTitle(title);
        return entityManager.persist(media);
    }

    private User user(String username) {
        return user(username, null);
    }

    private User user(String username, String avatarUrl) {
        User user = User.create(
                username + "@cabinet.test",
                username,
                username,
                "hash"
        );
        user.setAvatarUlr(avatarUrl);
        return entityManager.persist(user);
    }

    private void reference(Media media, String externalId) {
        ExternalReference reference = new ExternalReference();
        reference.setMedia(media);
        reference.setSource(ExternalSource.TMDB);
        reference.setExternalId(externalId);
        reference.setPrimaryReference(true);
        entityManager.persist(reference);
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
        review.setContent("Review de " + user.getUsername());
        review.setVisibility(visibility);
        return entityManager.persist(review);
    }

    private ReviewLike like(Review review, User user) {
        ReviewLike like = new ReviewLike();
        like.setReview(review);
        like.setUser(user);
        return entityManager.persist(like);
    }

    private void setCreatedAt(Review review, Instant createdAt) {
        entityManager.getEntityManager()
                .createQuery("""
                        update Review r
                        set r.createdAt = :createdAt, r.publishedAt = :createdAt
                        where r.id = :reviewId
                        """)
                .setParameter("createdAt", createdAt)
                .setParameter("reviewId", review.getId())
                .executeUpdate();
    }

    private void setCreatedAt(ReviewLike like, Instant createdAt) {
        entityManager.getEntityManager()
                .createQuery("update ReviewLike rl set rl.createdAt = :createdAt where rl.id = :likeId")
                .setParameter("createdAt", createdAt)
                .setParameter("likeId", like.getId())
                .executeUpdate();
    }
}
