package com.scriptles.cabinet.user.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.notifications.repository.NotificationRepository;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.entity.UserFollow;
import com.scriptles.cabinet.user.enums.FollowState;
import com.scriptles.cabinet.user.enums.FollowStatus;
import com.scriptles.cabinet.user.enums.Visibility;
import com.scriptles.cabinet.user.repository.UserBlockRepository;
import com.scriptles.cabinet.user.repository.UserFollowRepository;
import com.scriptles.cabinet.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SocialGraphServiceTest {
    @Mock UserRepository userRepository;
    @Mock UserFollowRepository followRepository;
    @Mock UserBlockRepository blockRepository;
    @Mock NotificationRepository notificationRepository;
    @Mock SocialAccessPolicy accessPolicy;
    @Mock SocialCursorCodec cursorCodec;
    @InjectMocks SocialGraphService service;

    @Test
    void publicFollowIsAcceptedAndUpdatesExactCounters() {
        User follower = user(Visibility.PUBLIC);
        User followed = user(Visibility.PUBLIC);
        followed.setFollowersCount(4);
        follower.setFollowingCount(2);
        locked(follower, followed);
        when(followRepository.findByFollowerIdAndFollowedId(follower.getId(), followed.getId()))
                .thenReturn(Optional.empty());

        var response = service.follow(follower.getId(), followed.getId());

        ArgumentCaptor<UserFollow> captor = ArgumentCaptor.forClass(UserFollow.class);
        verify(followRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(FollowStatus.ACCEPTED);
        assertThat(captor.getValue().getAcceptedAt()).isNotNull();
        assertThat(response.state()).isEqualTo(FollowState.FOLLOWING);
        assertThat(follower.getFollowingCount()).isEqualTo(3);
        assertThat(followed.getFollowersCount()).isEqualTo(5);
    }

    @Test
    void privateFollowCreatesPendingRequestWithoutChangingCounters() {
        User follower = user(Visibility.PUBLIC);
        User followed = user(Visibility.PRIVATE);
        locked(follower, followed);
        when(followRepository.findByFollowerIdAndFollowedId(follower.getId(), followed.getId()))
                .thenReturn(Optional.empty());

        var response = service.follow(follower.getId(), followed.getId());

        ArgumentCaptor<UserFollow> captor = ArgumentCaptor.forClass(UserFollow.class);
        verify(followRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(FollowStatus.PENDING);
        assertThat(captor.getValue().getAcceptedAt()).isNull();
        assertThat(response.state()).isEqualTo(FollowState.PENDING);
        assertThat(follower.getFollowingCount()).isZero();
        assertThat(followed.getFollowersCount()).isZero();
    }

    @Test
    void acceptingPendingRequestUpdatesBothCountersOnce() {
        User requester = user(Visibility.PUBLIC);
        User followed = user(Visibility.PRIVATE);
        locked(requester, followed);
        UserFollow follow = new UserFollow();
        follow.setFollower(requester);
        follow.setFollowed(followed);
        follow.setStatus(FollowStatus.PENDING);
        when(followRepository.findByFollowerIdAndFollowedId(requester.getId(), followed.getId()))
                .thenReturn(Optional.of(follow));

        service.accept(followed.getId(), requester.getId());

        assertThat(follow.getStatus()).isEqualTo(FollowStatus.ACCEPTED);
        assertThat(requester.getFollowingCount()).isEqualTo(1);
        assertThat(followed.getFollowersCount()).isEqualTo(1);
    }

    @Test
    void blockRemovesAcceptedEdgesInBothDirectionsAndNotifications() {
        User first = user(Visibility.PUBLIC);
        User second = user(Visibility.PUBLIC);
        first.setFollowersCount(1);
        first.setFollowingCount(1);
        second.setFollowersCount(1);
        second.setFollowingCount(1);
        locked(first, second);
        UserFollow firstToSecond = accepted(first, second);
        UserFollow secondToFirst = accepted(second, first);
        when(followRepository.findByFollowerIdAndFollowedId(first.getId(), second.getId()))
                .thenReturn(Optional.of(firstToSecond));
        when(followRepository.findByFollowerIdAndFollowedId(second.getId(), first.getId()))
                .thenReturn(Optional.of(secondToFirst));

        service.block(first.getId(), second.getId());

        assertThat(first.getFollowersCount()).isZero();
        assertThat(first.getFollowingCount()).isZero();
        assertThat(second.getFollowersCount()).isZero();
        assertThat(second.getFollowingCount()).isZero();
        verify(followRepository).delete(firstToSecond);
        verify(followRepository).delete(secondToFirst);
        verify(notificationRepository).deleteBetweenUsers(first.getId(), second.getId());
        verify(blockRepository).save(any());
    }

    @Test
    void rejectsSelfRelationshipBeforeDatabaseAccess() {
        UUID userId = UUID.randomUUID();
        assertThatThrownBy(() -> service.follow(userId, userId))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("SELF_RELATIONSHIP_NOT_ALLOWED"));
        verify(userRepository, never()).findAllLockedByIdIn(any());
    }

    private void locked(User first, User second) {
        when(userRepository.findAllLockedByIdIn(any()))
                .thenReturn(List.of(first, second));
    }

    private User user(Visibility visibility) {
        User user = User.create(
                UUID.randomUUID() + "@cabinet.test",
                UUID.randomUUID().toString().substring(0, 12),
                "User",
                "hash"
        );
        user.setId(UUID.randomUUID());
        user.setActive(true);
        user.setProfileVisibility(visibility);
        return user;
    }

    private UserFollow accepted(User follower, User followed) {
        UserFollow follow = new UserFollow();
        follow.setFollower(follower);
        follow.setFollowed(followed);
        follow.setStatus(FollowStatus.ACCEPTED);
        return follow;
    }
}
