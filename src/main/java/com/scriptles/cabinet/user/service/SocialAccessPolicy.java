package com.scriptles.cabinet.user.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.enums.FollowState;
import com.scriptles.cabinet.user.enums.FollowStatus;
import com.scriptles.cabinet.user.enums.Visibility;
import com.scriptles.cabinet.user.repository.UserBlockRepository;
import com.scriptles.cabinet.user.repository.UserFollowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SocialAccessPolicy {
    private final UserFollowRepository followRepository;
    private final UserBlockRepository blockRepository;

    @Transactional(readOnly = true)
    public boolean isBlocked(UUID firstId, UUID secondId) {
        return firstId != null && secondId != null && !firstId.equals(secondId)
                && blockRepository.existsEitherDirection(firstId, secondId);
    }

    @Transactional(readOnly = true)
    public boolean isAcceptedFollower(UUID viewerId, UUID ownerId) {
        return viewerId != null && ownerId != null
                && followRepository.existsByFollowerIdAndFollowedIdAndStatus(
                        viewerId, ownerId, FollowStatus.ACCEPTED);
    }

    @Transactional(readOnly = true)
    public boolean canViewProfile(User profileUser, UUID viewerId) {
        if (viewerId != null && viewerId.equals(profileUser.getId())) return true;
        if (viewerId != null && isBlocked(viewerId, profileUser.getId())) return false;
        if (profileUser.getProfileVisibility() == null
                || profileUser.getProfileVisibility() == Visibility.PUBLIC) return true;
        return isAcceptedFollower(viewerId, profileUser.getId());
    }

    @Transactional(readOnly = true)
    public boolean canViewContent(UUID ownerId, UUID viewerId, Visibility visibility) {
        if (viewerId != null && viewerId.equals(ownerId)) return true;
        if (viewerId != null && isBlocked(viewerId, ownerId)) return false;
        Visibility effective = visibility == null ? Visibility.PUBLIC : visibility;
        if (effective == Visibility.PUBLIC) return true;
        if (effective == Visibility.PRIVATE) return false;
        return isAcceptedFollower(viewerId, ownerId);
    }

    @Transactional(readOnly = true)
    public Relationship relationship(UUID viewerId, UUID profileId) {
        if (viewerId == null || viewerId.equals(profileId)) {
            return new Relationship(FollowState.NONE, false);
        }
        FollowState state = followRepository.findByFollowerIdAndFollowedId(viewerId, profileId)
                .map(follow -> follow.getStatus() == FollowStatus.ACCEPTED
                        ? FollowState.FOLLOWING : FollowState.PENDING)
                .orElse(FollowState.NONE);
        boolean followsViewer = followRepository.existsByFollowerIdAndFollowedIdAndStatus(
                profileId, viewerId, FollowStatus.ACCEPTED);
        return new Relationship(state, followsViewer);
    }

    public ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }

    public record Relationship(FollowState state, boolean followsViewer) {
    }
}
