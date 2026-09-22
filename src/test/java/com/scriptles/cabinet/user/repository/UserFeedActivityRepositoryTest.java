package com.scriptles.cabinet.user.repository;

import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.entity.UserBlock;
import com.scriptles.cabinet.user.entity.UserBlockId;
import com.scriptles.cabinet.user.entity.UserFeedActivity;
import com.scriptles.cabinet.user.entity.UserFollow;
import com.scriptles.cabinet.user.entity.UserFollowId;
import com.scriptles.cabinet.user.enums.FeedActionType;
import com.scriptles.cabinet.user.enums.FollowStatus;
import com.scriptles.cabinet.user.enums.Visibility;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect")
class UserFeedActivityRepositoryTest {
    @Autowired EntityManager entityManager;
    @Autowired UserFeedActivityRepository repository;

    @Test
    void friendsFeedIncludesOnlyAcceptedUnblockedFriendsAndVisibleActions() {
        User viewer = persistUser("viewer");
        User acceptedFriend = persistUser("accepted");
        User pendingFriend = persistUser("pending");
        User blockedFriend = persistUser("blocked");
        Media media = persistMedia();
        follow(viewer, acceptedFriend, FollowStatus.ACCEPTED);
        follow(viewer, pendingFriend, FollowStatus.PENDING);
        follow(viewer, blockedFriend, FollowStatus.ACCEPTED);
        block(viewer, blockedFriend);

        UserFeedActivity publicAction = persistAction(acceptedFriend, media, FeedActionType.LIKED, Visibility.PUBLIC);
        UserFeedActivity followersAction = persistAction(acceptedFriend, media, FeedActionType.RATED, Visibility.FOLLOWERS);
        persistAction(acceptedFriend, media, FeedActionType.REVIEWED, Visibility.PRIVATE);
        persistAction(pendingFriend, media, FeedActionType.LIKED, Visibility.PUBLIC);
        persistAction(blockedFriend, media, FeedActionType.LIKED, Visibility.PUBLIC);
        entityManager.flush();

        var result = repository.findFeed(viewer.getId(), true, List.of(Visibility.PUBLIC, Visibility.FOLLOWERS),
                PageRequest.of(0, 10, Sort.unsorted()));

        assertThat(result.getContent()).extracting(UserFeedActivity::getId)
                .containsExactly(followersAction.getId(), publicAction.getId());
    }

    @Test
    void youFeedIncludesOnlyTheViewersOwnPrivateActionsAndPagesNewestFirst() {
        User viewer = persistUser("viewer");
        User other = persistUser("other");
        Media media = persistMedia();
        UserFeedActivity older = persistAction(viewer, media, FeedActionType.LIKED, Visibility.PRIVATE);
        older.setOccurredAt(Instant.parse("2026-01-01T00:00:00Z"));
        UserFeedActivity newer = persistAction(viewer, media, FeedActionType.RATED, Visibility.PUBLIC);
        newer.setOccurredAt(Instant.parse("2026-02-01T00:00:00Z"));
        persistAction(other, media, FeedActionType.LIKED, Visibility.PUBLIC);
        entityManager.flush();

        var result = repository.findFeed(viewer.getId(), false, List.of(Visibility.PUBLIC, Visibility.FOLLOWERS),
                PageRequest.of(0, 1, Sort.unsorted()));

        assertThat(result.getContent()).extracting(UserFeedActivity::getId).containsExactly(newer.getId());
        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    private User persistUser(String suffix) {
        User user = User.create(suffix + "@example.com", suffix, suffix, "hash");
        user.setProfileVisibility(Visibility.PRIVATE);
        entityManager.persist(user);
        return user;
    }

    private Media persistMedia() {
        Media media = new Media();
        media.setType(MediaType.MOVIE);
        media.setTitle("A movie");
        entityManager.persist(media);
        return media;
    }

    private UserFeedActivity persistAction(User user, Media media, FeedActionType action, Visibility visibility) {
        UserFeedActivity activity = new UserFeedActivity();
        activity.setUser(user);
        activity.setMedia(media);
        activity.setActionType(action);
        activity.setOccurredAt(Instant.now());
        activity.setVisibility(visibility);
        entityManager.persist(activity);
        return activity;
    }

    private void follow(User follower, User followed, FollowStatus status) {
        UserFollow follow = new UserFollow();
        follow.setId(new UserFollowId(follower.getId(), followed.getId()));
        follow.setFollower(follower);
        follow.setFollowed(followed);
        follow.setStatus(status);
        follow.setRequestedAt(Instant.now());
        if (status == FollowStatus.ACCEPTED) follow.setAcceptedAt(Instant.now());
        entityManager.persist(follow);
    }

    private void block(User blocker, User blocked) {
        UserBlock row = new UserBlock();
        row.setId(new UserBlockId(blocker.getId(), blocked.getId()));
        row.setBlocker(blocker);
        row.setBlocked(blocked);
        row.setCreatedAt(Instant.now());
        entityManager.persist(row);
    }
}
