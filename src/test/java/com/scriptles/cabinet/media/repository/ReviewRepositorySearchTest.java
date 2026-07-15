package com.scriptles.cabinet.media.repository;

import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.Review;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ReviewRepositorySearchTest {
    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ReviewRepository reviewRepository;

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

        var results = reviewRepository.searchRatedMedia(
                "matrix",
                Set.of(MediaType.MOVIE),
                Visibility.PUBLIC,
                PageRequest.of(0, 20)
        );

        assertThat(results.getContent())
                .extracting(projection -> projection.getMedia().getTitle())
                .containsExactly("Matrix B", "Matrix A");
        assertThat(results.getContent().getFirst().getAverageRating()).isEqualTo(5.0);
        assertThat(results.getContent().getFirst().getRatingCount()).isEqualTo(2);
    }

    private Media media(String title) {
        Media media = new Media();
        media.setType(MediaType.MOVIE);
        media.setTitle(title);
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

    private void reference(Media media, String externalId) {
        ExternalReference reference = new ExternalReference();
        reference.setMedia(media);
        reference.setSource(ExternalSource.TMDB);
        reference.setExternalId(externalId);
        reference.setPrimaryReference(true);
        entityManager.persist(reference);
    }

    private void review(Media media, User user, String rating, Visibility visibility) {
        Review review = new Review();
        review.setMedia(media);
        review.setUser(user);
        review.setRating(new BigDecimal(rating));
        review.setVisibility(visibility);
        entityManager.persist(review);
    }
}
