package com.scriptles.cabinet.notifications.repository;

import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.Rating;
import com.scriptles.cabinet.media.entity.Review;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.notifications.entity.Notification;
import com.scriptles.cabinet.notifications.enums.NotificationType;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.enums.Visibility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect")
class NotificationRepositoryTest {
    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void loadsReviewMediaThroughRatingWhenListingNotifications() {
        User recipient = persistUser("recipient");
        User actor = persistUser("actor");

        Media media = new Media();
        media.setType(MediaType.MOVIE);
        media.setTitle("The Matrix");
        entityManager.persist(media);

        Rating rating = new Rating();
        rating.setUser(recipient);
        rating.setMedia(media);
        rating.setValue(new BigDecimal("4.5"));
        rating.setVisibility(Visibility.PUBLIC);
        entityManager.persist(rating);

        Review review = new Review();
        review.setUser(recipient);
        review.setMedia(media);
        review.setRatingEntity(rating);
        review.setVisibility(Visibility.PUBLIC);
        entityManager.persist(review);

        Notification notification = new Notification();
        notification.setRecipient(recipient);
        notification.setActor(actor);
        notification.setType(NotificationType.REVIEW_LIKED);
        notification.setReview(review);
        notification.setActivityAt(Instant.parse("2026-07-20T12:00:00Z"));
        entityManager.persist(notification);
        entityManager.flush();
        entityManager.clear();

        Notification result = notificationRepository
                .findByRecipientIdOrderByActivityAtDescIdDesc(
                        recipient.getId(),
                        PageRequest.of(0, 20)
                )
                .getContent()
                .getFirst();

        assertThat(result.getReview().getMedia().getTitle()).isEqualTo("The Matrix");
    }

    private User persistUser(String username) {
        return entityManager.persist(User.create(
                username + "@cabinet.test",
                username,
                username,
                "hash"
        ));
    }
}
