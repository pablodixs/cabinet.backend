package com.scriptles.cabinet.user.repository;

import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.entity.UserBlock;
import com.scriptles.cabinet.user.entity.UserBlockId;
import com.scriptles.cabinet.user.entity.UserFollow;
import com.scriptles.cabinet.user.entity.UserFollowId;
import com.scriptles.cabinet.user.enums.FollowStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect")
class UserSocialRepositoryTest {
    @Autowired TestEntityManager entityManager;
    @Autowired UserFollowRepository followRepository;
    @Autowired UserBlockRepository blockRepository;

    @Test
    void keysetFollowerQueryOrdersRowsAndFiltersUsersBlockedByViewer() {
        User owner = user("owner");
        User older = user("older");
        User newer = user("newer");
        User viewer = user("viewer");
        follow(older, owner, Instant.parse("2026-07-20T10:00:00Z"));
        follow(newer, owner, Instant.parse("2026-07-21T10:00:00Z"));
        block(viewer, newer);
        entityManager.flush();
        entityManager.clear();

        List<UserFollow> rows = followRepository.findFirstFollowers(
                owner.getId(), viewer.getId(), PageRequest.of(0, 3));

        assertThat(rows).extracting(row -> row.getFollower().getUsername())
                .containsExactly("older");
        assertThat(blockRepository.existsEitherDirection(viewer.getId(), newer.getId())).isTrue();
    }

    @Test
    void keysetFollowerQueryContinuesAfterTimestampAndUserId() {
        User owner = user("owner-page");
        User first = user("first-page");
        User second = user("second-page");
        User third = user("third-page");
        User viewer = user("viewer-page");
        Instant acceptedAt = Instant.parse("2026-07-21T10:00:00Z");
        follow(first, owner, acceptedAt);
        follow(second, owner, acceptedAt);
        follow(third, owner, acceptedAt);
        entityManager.flush();
        entityManager.clear();

        List<UserFollow> firstPage = followRepository.findFirstFollowers(
                owner.getId(), viewer.getId(), PageRequest.of(0, 2));
        UserFollow last = firstPage.getLast();
        List<UserFollow> secondPage = followRepository.findFollowersAfter(
                owner.getId(), viewer.getId(), last.getAcceptedAt(), last.getFollower().getId(),
                PageRequest.of(0, 2));

        assertThat(firstPage).hasSize(2);
        assertThat(secondPage).hasSize(1);
        assertThat(secondPage.getFirst().getFollower().getId())
                .isNotIn(firstPage.stream().map(row -> row.getFollower().getId()).toList());
    }

    private User user(String username) {
        return entityManager.persist(User.create(
                username + "@cabinet.test", username, username, "hash"));
    }

    private void follow(User follower, User followed, Instant acceptedAt) {
        UserFollow follow = new UserFollow();
        follow.setId(new UserFollowId(follower.getId(), followed.getId()));
        follow.setFollower(follower);
        follow.setFollowed(followed);
        follow.setStatus(FollowStatus.ACCEPTED);
        follow.setRequestedAt(acceptedAt);
        follow.setAcceptedAt(acceptedAt);
        entityManager.persist(follow);
    }

    private void block(User blocker, User blocked) {
        UserBlock block = new UserBlock();
        block.setId(new UserBlockId(blocker.getId(), blocked.getId()));
        block.setBlocker(blocker);
        block.setBlocked(blocked);
        block.setCreatedAt(Instant.now());
        entityManager.persist(block);
    }
}
