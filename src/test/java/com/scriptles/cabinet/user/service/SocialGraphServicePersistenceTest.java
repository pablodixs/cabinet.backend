package com.scriptles.cabinet.user.service;

import com.scriptles.cabinet.notifications.entity.Notification;
import com.scriptles.cabinet.notifications.enums.NotificationType;
import com.scriptles.cabinet.notifications.repository.LocalReleaseReminderRepository;
import com.scriptles.cabinet.notifications.repository.NotificationRepository;
import com.scriptles.cabinet.notifications.service.NotificationDeliveryService;
import com.scriptles.cabinet.notifications.service.NotificationService;
import com.scriptles.cabinet.status.BackgroundJobRetention;
import com.scriptles.cabinet.status.BackgroundJobRunner;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.enums.FollowStatus;
import com.scriptles.cabinet.user.enums.FollowState;
import com.scriptles.cabinet.user.enums.Visibility;
import com.scriptles.cabinet.user.repository.UserFollowRepository;
import com.scriptles.cabinet.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect")
@Import({SocialGraphService.class, NotificationService.class})
class SocialGraphServicePersistenceTest {
    @Autowired TestEntityManager entityManager;
    @Autowired UserRepository userRepository;
    @Autowired UserFollowRepository followRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired SocialGraphService service;
    @MockitoBean NotificationDeliveryService notificationDeliveryService;
    @MockitoBean BackgroundJobRunner backgroundJobRunner;
    @MockitoBean BackgroundJobRetention backgroundJobRetention;
    @MockitoBean LocalReleaseReminderRepository localReleaseReminderRepository;
    @MockitoBean SocialAccessPolicy accessPolicy;
    @MockitoBean SocialCursorCodec cursorCodec;

    @Test
    void followingPublicUserPersistsAcceptedRelationshipAndCounters() {
        User follower = user("follower-persistence", Visibility.PUBLIC);
        User followed = user("followed-persistence", Visibility.PUBLIC);

        var response = service.follow(follower.getId(), followed.getId());

        entityManager.flush();
        entityManager.clear();

        var relationship = followRepository.findByFollowerIdAndFollowedId(
                follower.getId(), followed.getId()).orElseThrow();
        assertThat(relationship.getStatus()).isEqualTo(FollowStatus.ACCEPTED);
        assertThat(relationship.getAcceptedAt()).isNotNull();
        assertThat(response.state()).isEqualTo(FollowState.FOLLOWING);
        assertThat(userRepository.findById(follower.getId()).orElseThrow().getFollowingCount()).isEqualTo(1);
        assertThat(userRepository.findById(followed.getId()).orElseThrow().getFollowersCount()).isEqualTo(1);
        var notifications = notificationRepository.findByRecipientIdOrderByActivityAtDescIdDesc(
                followed.getId(), PageRequest.of(0, 10)).getContent();
        assertThat(notifications).singleElement().satisfies(notification -> {
            assertThat(notification.getType()).isEqualTo(NotificationType.FOLLOWED);
            assertThat(notification.getActor().getId()).isEqualTo(follower.getId());
        });
    }

    @Test
    void followingTheSamePublicUserAgainDoesNotDuplicateTheRelationshipOrNotification() {
        User follower = user("repeat-follower", Visibility.PUBLIC);
        User followed = user("repeat-followed", Visibility.PUBLIC);

        service.follow(follower.getId(), followed.getId());
        service.follow(follower.getId(), followed.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(followRepository.findByFollowerIdAndFollowedId(
                follower.getId(), followed.getId())).isPresent();
        assertThat(userRepository.findById(follower.getId()).orElseThrow().getFollowingCount()).isEqualTo(1);
        assertThat(userRepository.findById(followed.getId()).orElseThrow().getFollowersCount()).isEqualTo(1);
        assertThat(notificationRepository.findByRecipientIdOrderByActivityAtDescIdDesc(
                followed.getId(), PageRequest.of(0, 10)).getContent()).hasSize(1);
    }

    @Test
    void followingPrivateUserPersistsPendingRequestWithoutCountersOrNotification() {
        User follower = user("private-follower", Visibility.PUBLIC);
        User followed = user("private-followed", Visibility.PRIVATE);

        var response = service.follow(follower.getId(), followed.getId());

        entityManager.flush();
        entityManager.clear();

        var relationship = followRepository.findByFollowerIdAndFollowedId(
                follower.getId(), followed.getId()).orElseThrow();
        assertThat(relationship.getStatus()).isEqualTo(FollowStatus.PENDING);
        assertThat(relationship.getAcceptedAt()).isNull();
        assertThat(response.state()).isEqualTo(FollowState.PENDING);
        assertThat(userRepository.findById(follower.getId()).orElseThrow().getFollowingCount()).isZero();
        assertThat(userRepository.findById(followed.getId()).orElseThrow().getFollowersCount()).isZero();
        assertThat(notificationRepository.findByRecipientIdOrderByActivityAtDescIdDesc(
                followed.getId(), PageRequest.of(0, 10)).getContent()).isEmpty();
    }

    private User user(String username, Visibility visibility) {
        User user = User.create(username + "@cabinet.test", username, username, "hash");
        user.setProfileVisibility(visibility);
        return entityManager.persist(user);
    }
}
