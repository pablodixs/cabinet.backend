package com.scriptles.cabinet.user.service;

import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.enums.FollowStatus;
import com.scriptles.cabinet.user.enums.Visibility;
import com.scriptles.cabinet.user.repository.UserBlockRepository;
import com.scriptles.cabinet.user.repository.UserFollowRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SocialAccessPolicyTest {
    @Mock UserFollowRepository followRepository;
    @Mock UserBlockRepository blockRepository;
    @InjectMocks SocialAccessPolicy policy;

    @Test
    void acceptedFollowerUnlocksPrivateProfileAndFollowerContentButNotPrivateItems() {
        UUID viewerId = UUID.randomUUID();
        User owner = user(Visibility.PRIVATE);
        when(followRepository.existsByFollowerIdAndFollowedIdAndStatus(
                viewerId, owner.getId(), FollowStatus.ACCEPTED)).thenReturn(true);

        assertThat(policy.canViewProfile(owner, viewerId)).isTrue();
        assertThat(policy.canViewContent(owner.getId(), viewerId, Visibility.PUBLIC)).isTrue();
        assertThat(policy.canViewContent(owner.getId(), viewerId, Visibility.FOLLOWERS)).isTrue();
        assertThat(policy.canViewContent(owner.getId(), viewerId, Visibility.PRIVATE)).isFalse();
    }

    @Test
    void blockOverridesPublicProfileAndContent() {
        UUID viewerId = UUID.randomUUID();
        User owner = user(Visibility.PUBLIC);
        when(blockRepository.existsEitherDirection(viewerId, owner.getId())).thenReturn(true);

        assertThat(policy.canViewProfile(owner, viewerId)).isFalse();
        assertThat(policy.canViewContent(owner.getId(), viewerId, Visibility.PUBLIC)).isFalse();
    }

    @Test
    void anonymousViewerOnlySeesPublicResources() {
        User owner = user(Visibility.PRIVATE);
        assertThat(policy.canViewProfile(owner, null)).isFalse();
        assertThat(policy.canViewContent(owner.getId(), null, Visibility.PUBLIC)).isTrue();
        assertThat(policy.canViewContent(owner.getId(), null, Visibility.FOLLOWERS)).isFalse();
        assertThat(policy.canViewContent(owner.getId(), null, Visibility.PRIVATE)).isFalse();
    }

    private User user(Visibility visibility) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setProfileVisibility(visibility);
        return user;
    }
}
