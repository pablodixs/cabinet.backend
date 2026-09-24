package com.scriptles.cabinet.user.service;

import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.common.api.RichTextDocument;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.Review;
import com.scriptles.cabinet.media.repository.ReviewRepository;
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
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserFeedService {
    private final UserFeedActivityRepository repository;
    private final ReviewRepository reviewRepository;

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
    public PageResponse<FeedActivityResponse> find(UUID viewerId, boolean friendsOnly, boolean interactionsOnly, int page, int size) {
        var result = repository.findFeed(viewerId, friendsOnly, interactionsOnly,
                List.of(Visibility.PUBLIC, Visibility.FOLLOWERS),
                PageRequest.of(page, size, Sort.unsorted()));
        return enrich(result, friendsOnly ? List.of(Visibility.PUBLIC, Visibility.FOLLOWERS)
                : List.of(Visibility.PUBLIC, Visibility.FOLLOWERS, Visibility.PRIVATE));
    }

    @Transactional(readOnly = true)
    public PageResponse<FeedActivityResponse> findForMedia(UUID viewerId, UUID mediaId, int page, int size) {
        var result = repository.findFriendsActivityForMedia(viewerId, mediaId,
                List.of(Visibility.PUBLIC, Visibility.FOLLOWERS),
                PageRequest.of(page, size, Sort.unsorted()));
        return enrich(result, List.of(Visibility.PUBLIC, Visibility.FOLLOWERS));
    }
    private PageResponse<FeedActivityResponse> enrich(org.springframework.data.domain.Page<UserFeedActivity> page,
                                                       List<Visibility> visibilities) {
        if (page.isEmpty()) return PageResponse.from(page.map(activity ->
                FeedActivityResponse.from(activity, null, null, false, false, false)));
        Set<UUID> userIds = page.getContent().stream().map(a -> a.getUser().getId()).collect(Collectors.toSet());
        Set<UUID> mediaIds = page.getContent().stream().map(a -> a.getMedia().getId()).collect(Collectors.toSet());
        Map<String, CardDetails> details = new HashMap<>();
        for (UserFeedActivity activity : repository.findCardDetails(userIds, mediaIds,
                List.of(FeedActionType.LIKED, FeedActionType.RATED, FeedActionType.REVIEWED), visibilities)) {
            CardDetails card = details.computeIfAbsent(key(activity), ignored -> new CardDetails());
            switch (activity.getActionType()) {
                case LIKED -> card.liked = true;
                case RATED -> card.rating = activity.getRating();
                case REVIEWED -> {
                    card.reviewed = true;
                    card.review = activity.getReview();
                    card.containsSpoilers = activity.isContainsSpoilers();
                    if (card.rating == null) card.rating = activity.getRating();
                }
                default -> { }
            }
        }
        for (Review review : reviewRepository.findRichContentForFeed(userIds, mediaIds, visibilities)) {
            CardDetails card = details.get(key(review.getUser().getId(), review.getMedia().getId()));
            if (card != null && review.getContent() != null && review.getContent().equals(card.review)) {
                card.richContent = RichTextDocument.parse(review.getRichContent());
            }
        }
        return PageResponse.from(page.map(activity -> {
            CardDetails card = details.getOrDefault(key(activity), new CardDetails());
            return FeedActivityResponse.from(activity, card.rating, card.review, card.richContent,
                    card.containsSpoilers, card.liked, card.reviewed);
        }));
    }

    private static String key(UserFeedActivity activity) {
        return key(activity.getUser().getId(), activity.getMedia().getId());
    }

    private static String key(UUID userId, UUID mediaId) {
        return userId + ":" + mediaId;
    }

    private static class CardDetails {
        BigDecimal rating;
        String review;
        JsonNode richContent;
        boolean containsSpoilers;
        boolean liked;
        boolean reviewed;
    }
}
