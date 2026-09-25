package com.scriptles.cabinet.user.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.common.api.CursorPageResponse;
import com.scriptles.cabinet.notifications.repository.NotificationRepository;
import com.scriptles.cabinet.notifications.service.NotificationService;
import com.scriptles.cabinet.user.dto.response.BlockedUserResponse;
import com.scriptles.cabinet.user.dto.response.FollowActionResponse;
import com.scriptles.cabinet.user.dto.response.SocialUserResponse;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.entity.UserBlock;
import com.scriptles.cabinet.user.entity.UserBlockId;
import com.scriptles.cabinet.user.entity.UserFollow;
import com.scriptles.cabinet.user.entity.UserFollowId;
import com.scriptles.cabinet.user.enums.FollowState;
import com.scriptles.cabinet.user.enums.FollowStatus;
import com.scriptles.cabinet.user.enums.Visibility;
import com.scriptles.cabinet.user.repository.UserBlockRepository;
import com.scriptles.cabinet.user.repository.UserFollowRepository;
import com.scriptles.cabinet.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SocialGraphService {
    private final UserRepository userRepository;
    private final UserFollowRepository followRepository;
    private final UserBlockRepository blockRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final SocialAccessPolicy accessPolicy;
    private final SocialCursorCodec cursorCodec;

    @Transactional
    public FollowActionResponse follow(UUID followerId, UUID followedId) {
        rejectSelf(followerId, followedId);
        Map<UUID, User> users = lockUsers(followerId, followedId);
        User follower = requireActive(users.get(followerId));
        User followed = requireActive(users.get(followedId));
        if (blockRepository.existsEitherDirection(followerId, followedId)) throw userNotFound();

        UserFollow follow = followRepository.findByFollowerIdAndFollowedId(followerId, followedId)
                .orElse(null);
        boolean publicProfile = followed.getProfileVisibility() == null
                || followed.getProfileVisibility() == Visibility.PUBLIC;
        if (follow == null) {
            follow = new UserFollow();
            follow.setId(new UserFollowId(followerId, followedId));
            follow.setFollower(follower);
            follow.setFollowed(followed);
            follow.setRequestedAt(Instant.now());
            follow.setStatus(publicProfile ? FollowStatus.ACCEPTED : FollowStatus.PENDING);
            if (publicProfile) {
                follow.setAcceptedAt(Instant.now());
                incrementCounters(follower, followed);
            }
            followRepository.saveAndFlush(follow);
            if (publicProfile && notificationService != null) notificationService.followed(follower, followed);
        } else if (follow.getStatus() == FollowStatus.PENDING && publicProfile) {
            follow.setStatus(FollowStatus.ACCEPTED);
            follow.setAcceptedAt(Instant.now());
            incrementCounters(follower, followed);
            followRepository.saveAndFlush(follow);
            if (notificationService != null) notificationService.followed(follower, followed);
        }
        return new FollowActionResponse(followedId, state(follow.getStatus()));
    }

    @Transactional
    public void unfollow(UUID followerId, UUID followedId) {
        rejectSelf(followerId, followedId);
        Map<UUID, User> users = lockUsers(followerId, followedId);
        requireActive(users.get(followerId));
        requireActive(users.get(followedId));
        followRepository.findByFollowerIdAndFollowedId(followerId, followedId).ifPresent(follow -> {
            if (follow.getStatus() == FollowStatus.ACCEPTED) {
                decrementCounters(users.get(followerId), users.get(followedId));
            }
            followRepository.delete(follow);
        });
        followRepository.flush();
    }

    @Transactional
    public FollowActionResponse accept(UUID followedId, UUID requesterId) {
        rejectSelf(followedId, requesterId);
        Map<UUID, User> users = lockUsers(followedId, requesterId);
        User followed = requireActive(users.get(followedId));
        User requester = requireActive(users.get(requesterId));
        if (blockRepository.existsEitherDirection(followedId, requesterId)) throw followRequestNotFound();
        UserFollow follow = followRepository.findByFollowerIdAndFollowedId(requesterId, followedId)
                .filter(candidate -> candidate.getStatus() == FollowStatus.PENDING)
                .orElseThrow(this::followRequestNotFound);
        follow.setStatus(FollowStatus.ACCEPTED);
        follow.setAcceptedAt(Instant.now());
        incrementCounters(requester, followed);
        followRepository.saveAndFlush(follow);
        if (notificationService != null) notificationService.followed(requester, followed);
        return new FollowActionResponse(requesterId, FollowState.FOLLOWING);
    }

    @Transactional
    public void reject(UUID followedId, UUID requesterId) {
        rejectSelf(followedId, requesterId);
        lockUsers(followedId, requesterId);
        followRepository.findByFollowerIdAndFollowedId(requesterId, followedId)
                .filter(follow -> follow.getStatus() == FollowStatus.PENDING)
                .ifPresent(followRepository::delete);
        followRepository.flush();
    }

    @Transactional
    public void removeFollower(UUID followedId, UUID followerId) {
        rejectSelf(followedId, followerId);
        Map<UUID, User> users = lockUsers(followedId, followerId);
        requireActive(users.get(followedId));
        requireActive(users.get(followerId));
        followRepository.findByFollowerIdAndFollowedId(followerId, followedId).ifPresent(follow -> {
            if (follow.getStatus() == FollowStatus.ACCEPTED) {
                decrementCounters(users.get(followerId), users.get(followedId));
            }
            followRepository.delete(follow);
        });
        followRepository.flush();
    }

    @Transactional
    public void block(UUID blockerId, UUID blockedId) {
        rejectSelf(blockerId, blockedId);
        Map<UUID, User> users = lockUsers(blockerId, blockedId);
        User blocker = requireActive(users.get(blockerId));
        User blocked = requireActive(users.get(blockedId));

        removeEdge(users, blockerId, blockedId);
        removeEdge(users, blockedId, blockerId);
        UserBlockId id = new UserBlockId(blockerId, blockedId);
        if (!blockRepository.existsById(id)) {
            UserBlock block = new UserBlock();
            block.setId(id);
            block.setBlocker(blocker);
            block.setBlocked(blocked);
            block.setCreatedAt(Instant.now());
            blockRepository.save(block);
        }
        notificationRepository.deleteBetweenUsers(blockerId, blockedId);
        followRepository.flush();
        blockRepository.flush();
    }

    @Transactional
    public void unblock(UUID blockerId, UUID blockedId) {
        rejectSelf(blockerId, blockedId);
        lockUsers(blockerId, blockedId);
        blockRepository.deleteById(new UserBlockId(blockerId, blockedId));
        blockRepository.flush();
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<SocialUserResponse> followers(
            String username, UUID viewerId, String cursor, int size) {
        User profile = findAccessibleProfile(username, viewerId);
        SocialCursorCodec.Position position = cursorCodec.decode(cursor);
        List<UserFollow> rows = position == null
                ? followRepository.findFirstFollowers(profile.getId(), viewerId, page(size))
                : followRepository.findFollowersAfter(
                        profile.getId(), viewerId, position.timestamp(), position.userId(), page(size));
        return socialPage(rows, size, viewerId, UserFollow::getFollower,
                UserFollow::getAcceptedAt);
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<SocialUserResponse> following(
            String username, UUID viewerId, String cursor, int size) {
        User profile = findAccessibleProfile(username, viewerId);
        SocialCursorCodec.Position position = cursorCodec.decode(cursor);
        List<UserFollow> rows = position == null
                ? followRepository.findFirstFollowing(profile.getId(), viewerId, page(size))
                : followRepository.findFollowingAfter(
                        profile.getId(), viewerId, position.timestamp(), position.userId(), page(size));
        return socialPage(rows, size, viewerId, UserFollow::getFollowed,
                UserFollow::getAcceptedAt);
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<SocialUserResponse> incomingRequests(
            UUID userId, String cursor, int size) {
        SocialCursorCodec.Position position = cursorCodec.decode(cursor);
        List<UserFollow> rows = position == null
                ? followRepository.findFirstIncomingRequests(userId, page(size))
                : followRepository.findIncomingRequestsAfter(
                        userId, position.timestamp(), position.userId(), page(size));
        return socialPage(rows, size, userId, UserFollow::getFollower,
                UserFollow::getRequestedAt);
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<SocialUserResponse> outgoingRequests(
            UUID userId, String cursor, int size) {
        SocialCursorCodec.Position position = cursorCodec.decode(cursor);
        List<UserFollow> rows = position == null
                ? followRepository.findFirstOutgoingRequests(userId, page(size))
                : followRepository.findOutgoingRequestsAfter(
                        userId, position.timestamp(), position.userId(), page(size));
        return socialPage(rows, size, userId, UserFollow::getFollowed,
                UserFollow::getRequestedAt);
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<BlockedUserResponse> blockedUsers(
            UUID userId, String cursor, int size) {
        SocialCursorCodec.Position position = cursorCodec.decode(cursor);
        List<UserBlock> rows = position == null
                ? blockRepository.findFirstBlockedUsers(userId, page(size))
                : blockRepository.findBlockedUsersAfter(
                        userId, position.timestamp(), position.userId(), page(size));
        boolean hasMore = rows.size() > size;
        List<UserBlock> visible = rows.stream().limit(size).toList();
        String next = hasMore && !visible.isEmpty()
                ? cursorCodec.encode(visible.getLast().getCreatedAt(), visible.getLast().getBlocked().getId())
                : null;
        return new CursorPageResponse<>(visible.stream().map(block -> new BlockedUserResponse(
                block.getBlocked().getId(),
                block.getBlocked().getUsername(),
                block.getBlocked().getDisplayName(),
                block.getBlocked().getAvatarUlr(),
                block.getBlocked().getAccountTier()
                        == com.scriptles.cabinet.user.enums.AccountTier.PRO,
                block.getCreatedAt()
        )).toList(), next, hasMore);
    }

    private CursorPageResponse<SocialUserResponse> socialPage(
            List<UserFollow> rows,
            int size,
            UUID viewerId,
            Function<UserFollow, User> userExtractor,
            Function<UserFollow, Instant> timestampExtractor
    ) {
        boolean hasMore = rows.size() > size;
        List<UserFollow> visible = rows.stream().limit(size).toList();
        List<User> users = visible.stream().map(userExtractor).toList();
        RelationshipBatch relationships = relationships(viewerId,
                users.stream().map(User::getId).toList());
        List<SocialUserResponse> items = new java.util.ArrayList<>(visible.size());
        for (int index = 0; index < visible.size(); index++) {
            UserFollow row = visible.get(index);
            User user = users.get(index);
            items.add(new SocialUserResponse(
                    user.getId(), user.getUsername(), user.getDisplayName(), user.getAvatarUlr(),
                    user.getAccountTier() == com.scriptles.cabinet.user.enums.AccountTier.PRO,
                    isPrivate(user), relationships.states().getOrDefault(user.getId(), FollowState.NONE),
                    relationships.followingViewer().contains(user.getId()), timestampExtractor.apply(row)
            ));
        }
        String next = hasMore && !visible.isEmpty()
                ? cursorCodec.encode(timestampExtractor.apply(visible.getLast()),
                        userExtractor.apply(visible.getLast()).getId())
                : null;
        return new CursorPageResponse<>(items, next, hasMore);
    }

    public RelationshipBatch relationships(UUID viewerId, Collection<UUID> userIds) {
        if (viewerId == null || userIds.isEmpty()) return new RelationshipBatch(Map.of(), Set.of());
        Map<UUID, FollowState> states = followRepository.findOutgoingStates(viewerId, userIds)
                .stream().collect(Collectors.toMap(
                        UserFollowRepository.OutgoingStateProjection::getUserId,
                        projection -> state(projection.getStatus())
                ));
        Set<UUID> followingViewer = new HashSet<>(
                followRepository.findUsersFollowingViewer(viewerId, userIds));
        return new RelationshipBatch(states, followingViewer);
    }

    private User findAccessibleProfile(String username, UUID viewerId) {
        User profile = userRepository.findByUsernameIgnoreCase(username.trim())
                .filter(user -> Boolean.TRUE.equals(user.getActive()))
                .orElseThrow(this::profileNotFound);
        if (!accessPolicy.canViewProfile(profile, viewerId)) throw profileNotFound();
        return profile;
    }

    private void removeEdge(Map<UUID, User> users, UUID followerId, UUID followedId) {
        followRepository.findByFollowerIdAndFollowedId(followerId, followedId).ifPresent(follow -> {
            if (follow.getStatus() == FollowStatus.ACCEPTED) {
                decrementCounters(users.get(followerId), users.get(followedId));
            }
            followRepository.delete(follow);
        });
    }

    private Map<UUID, User> lockUsers(UUID firstId, UUID secondId) {
        List<UUID> ids = List.of(firstId, secondId).stream()
                .distinct().sorted(Comparator.comparing(UUID::toString)).toList();
        Map<UUID, User> users = userRepository.findAllLockedByIdIn(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        if (users.size() != ids.size()) throw userNotFound();
        return users;
    }

    private User requireActive(User user) {
        if (user == null || !Boolean.TRUE.equals(user.getActive())) throw userNotFound();
        return user;
    }

    private void incrementCounters(User follower, User followed) {
        follower.setFollowingCount(follower.getFollowingCount() + 1);
        followed.setFollowersCount(followed.getFollowersCount() + 1);
    }

    private void decrementCounters(User follower, User followed) {
        follower.setFollowingCount(follower.getFollowingCount() - 1);
        followed.setFollowersCount(followed.getFollowersCount() - 1);
    }

    private PageRequest page(int size) {
        return PageRequest.of(0, size + 1);
    }

    private boolean isPrivate(User user) {
        return user.getProfileVisibility() != null
                && user.getProfileVisibility() != Visibility.PUBLIC;
    }

    private FollowState state(FollowStatus status) {
        return status == FollowStatus.ACCEPTED ? FollowState.FOLLOWING : FollowState.PENDING;
    }

    private void rejectSelf(UUID firstId, UUID secondId) {
        if (firstId.equals(secondId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SELF_RELATIONSHIP_NOT_ALLOWED",
                    "Não é possível criar esta relação consigo mesmo");
        }
    }

    private ApiException userNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Usuário não encontrado");
    }

    private ApiException profileNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "USER_PROFILE_NOT_FOUND", "Perfil não encontrado");
    }

    private ApiException followRequestNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "FOLLOW_REQUEST_NOT_FOUND",
                "Solicitação para seguir não encontrada");
    }

    public record RelationshipBatch(Map<UUID, FollowState> states, Set<UUID> followingViewer) {
    }
}
