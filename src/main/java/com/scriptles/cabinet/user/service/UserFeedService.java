package com.scriptles.cabinet.user.service;

import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.user.dto.response.FeedActivityResponse;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.entity.UserFeedActivity;
import com.scriptles.cabinet.user.enums.FeedActionType;
import com.scriptles.cabinet.user.enums.Visibility;
import com.scriptles.cabinet.user.repository.UserFeedActivityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserFeedService {
    private final UserFeedActivityRepository repository;

    @Transactional
    public void record(User user, Media media, FeedActionType action, Instant occurredAt,
                      Visibility visibility, BigDecimal rating, String review, boolean containsSpoilers) {
        UserFeedActivity activity = repository.findByUserIdAndMediaIdAndActionType(
                user.getId(), media.getId(), action).orElseGet(UserFeedActivity::new);
        activity.setUser(user);
        activity.setMedia(media);
        activity.setActionType(action);
        activity.setOccurredAt(occurredAt == null ? Instant.now() : occurredAt);
        activity.setVisibility(visibility == null ? Visibility.PUBLIC : visibility);
        activity.setRating(rating);
        activity.setReview(review);
        activity.setContainsSpoilers(containsSpoilers);
        repository.save(activity);
    }

    @Transactional
    public void remove(UUID userId, UUID mediaId, FeedActionType action) {
        repository.deleteByUserIdAndMediaIdAndActionType(userId, mediaId, action);
    }

    @Transactional(readOnly = true)
    public PageResponse<FeedActivityResponse> find(UUID viewerId, boolean friendsOnly, int page, int size) {
        var result = repository.findFeed(viewerId, friendsOnly,
                List.of(Visibility.PUBLIC, Visibility.FOLLOWERS),
                PageRequest.of(page, size, Sort.unsorted()));
        return PageResponse.from(result.map(FeedActivityResponse::from));
    }

    @Transactional(readOnly = true)
    public PageResponse<FeedActivityResponse> findForMedia(UUID viewerId, UUID mediaId, int page, int size) {
        var result = repository.findFriendsActivityForMedia(viewerId, mediaId,
                List.of(Visibility.PUBLIC, Visibility.FOLLOWERS),
                PageRequest.of(page, size, Sort.unsorted()));
        return PageResponse.from(result.map(FeedActivityResponse::from));
    }
}
