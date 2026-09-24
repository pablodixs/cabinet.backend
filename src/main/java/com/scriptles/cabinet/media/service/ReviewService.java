package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.common.outbox.DomainEventType;
import com.scriptles.cabinet.common.outbox.DomainOutboxPublisher;
import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.common.api.RichTextDocument;
import com.scriptles.cabinet.media.dto.request.UpsertReviewRequest;
import com.scriptles.cabinet.media.dto.response.ArtworkOptionResponse;
import com.scriptles.cabinet.media.dto.response.ReviewBackdropSelectionResponse;
import com.scriptles.cabinet.media.dto.response.MediaSearchItemResponse;
import com.scriptles.cabinet.media.dto.response.PopularReviewResponse;
import com.scriptles.cabinet.media.dto.response.ReviewLikerResponse;
import com.scriptles.cabinet.media.dto.response.ReviewResponse;
import com.scriptles.cabinet.media.dto.response.ReviewWithMediaResponse;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.Rating;
import com.scriptles.cabinet.media.entity.Review;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.media.repository.MediaLikeRepository;
import com.scriptles.cabinet.media.repository.RatingRepository;
import com.scriptles.cabinet.media.repository.ReviewLikeRepository;
import com.scriptles.cabinet.media.repository.ReviewRepository;
import com.scriptles.cabinet.media.validation.RatingValue;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.entity.UserMediaActivity;
import com.scriptles.cabinet.user.enums.ProfileActivityType;
import com.scriptles.cabinet.user.enums.Visibility;
import com.scriptles.cabinet.user.repository.UserMediaActivityRepository;
import com.scriptles.cabinet.user.repository.UserRepository;
import com.scriptles.cabinet.user.service.UserMediaService;
import com.scriptles.cabinet.user.service.UserFeedService;
import com.scriptles.cabinet.profile.entity.Profile;
import com.scriptles.cabinet.profile.repository.ProfileRepository;
import com.scriptles.cabinet.profile.service.ProfileService;
import com.scriptles.cabinet.user.enums.FeedActionType;
import com.scriptles.cabinet.user.service.SocialAccessPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ReviewService {
    private final ReviewRepository reviewRepository;
    private final ReviewLikeRepository reviewLikeRepository;
    private final MediaLikeRepository mediaLikeRepository;
    private final UserRepository userRepository;
    private final MediaRepository mediaRepository;
    private final RatingRepository ratingRepository;
    private final UserMediaActivityRepository userMediaActivityRepository;
    private final UserMediaService userMediaService;
    private final MediaSearchItemAssembler mediaSearchItemAssembler;
    private final MediaConsumptionPolicy mediaConsumptionPolicy;
    private final SocialAccessPolicy socialAccessPolicy;
    private final MediaCommunityCacheInvalidator communityCacheInvalidator;
    private final UserFeedService userFeedService;
    private final UserMediaArtworkService userMediaArtworkService;
    private final ProfileRepository profileRepository;
    private final ProfileService profileService;
    private final MediaLikeService mediaLikeService;
    private final DomainOutboxPublisher domainOutboxPublisher;

    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> findPublic(
            UUID userId,
            UUID mediaId,
            int page,
            int size
    ) {
        validateMediaExists(mediaId);

        PageRequest pageable = PageRequest.of(
                        page,
                        size,
                        Sort.by(Sort.Direction.DESC, "publishedAt")
                                .and(Sort.by(Sort.Direction.DESC, "id"))
                );
        Page<Review> reviews = userId == null
                ? reviewRepository.findByMediaIdAndVisibility(mediaId, Visibility.PUBLIC, pageable)
                : reviewRepository.findAccessibleByMediaId(mediaId, userId, pageable);
        return new PageResponse<>(
                responses(reviews.getContent(), userId),
                reviews.getNumber(),
                reviews.getSize(),
                reviews.getTotalElements(),
                reviews.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> findPopular(UUID userId, UUID mediaId) {
        validateMediaExists(mediaId);
        List<UUID> reviewIds = userId == null
                ? reviewRepository.findPopularIds(mediaId, Visibility.PUBLIC, PageRequest.of(0, 3))
                : reviewRepository.findAccessiblePopularIds(mediaId, userId, PageRequest.of(0, 3));
        if (reviewIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, Review> reviewsById = reviewRepository.findAllByIdIn(reviewIds)
                .stream()
                .collect(Collectors.toMap(Review::getId, review -> review));
        return responses(
                reviewIds.stream()
                        .map(reviewsById::get)
                        .filter(Objects::nonNull)
                        .toList(),
                userId
        );
    }

    @Transactional(readOnly = true)
    public List<PopularReviewResponse> findGloballyPopular(UUID userId, int limit) {
        List<UUID> reviewIds = userId == null
                ? reviewRepository.findGloballyPopularIds(Visibility.PUBLIC, PageRequest.of(0, limit))
                : reviewRepository.findGloballyAccessiblePopularIds(userId, PageRequest.of(0, limit));
        if (reviewIds.isEmpty()) {
            return List.of();
        }

        Map<UUID, Review> reviewsById = reviewRepository.findAllByIdIn(reviewIds)
                .stream()
                .collect(Collectors.toMap(Review::getId, review -> review));
        List<Review> reviews = reviewIds.stream()
                .map(reviewsById::get)
                .filter(Objects::nonNull)
                .toList();
        List<ReviewResponse> reviewResponses = responses(reviews, userId);
        List<Media> media = reviews.stream()
                .map(Review::getMedia)
                .collect(Collectors.toMap(
                        Media::getId,
                        item -> item,
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ))
                .values()
                .stream()
                .toList();
        Map<UUID, MediaSearchItemResponse> mediaById = mediaSearchItemAssembler.fromImported(media)
                .stream()
                .collect(Collectors.toMap(MediaSearchItemResponse::id, item -> item));

        return reviewResponses.stream()
                .map(review -> new PopularReviewResponse(review, mediaById.get(review.mediaId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> findRecent(UUID userId, UUID mediaId) {
        validateMediaExists(mediaId);
        List<Review> reviews = userId == null
                ? reviewRepository.findTop3ByRatingMediaIdAndRatingVisibilityOrderByCreatedAtDescIdDesc(
                        mediaId, Visibility.PUBLIC)
                : reviewRepository.findAccessibleRecent(mediaId, userId, PageRequest.of(0, 3));
        return responses(reviews, userId);
    }

    @Transactional(readOnly = true)
    public Optional<ReviewResponse> findMine(UUID userId, UUID mediaId) {
        return reviewRepository.findByUserIdAndMediaId(userId, mediaId)
                .filter(this::hasContent)
                .map(review -> response(review, userId));
    }

    @Transactional(readOnly = true)
    public PageResponse<ReviewWithMediaResponse> findMine(UUID userId, int page, int size) {
        Page<Review> reviews = reviewRepository.findMine(
                userId,
                PageRequest.of(page, size)
        );
        List<Media> media = reviews.getContent().stream().map(Review::getMedia).toList();
        Map<UUID, MediaSearchItemResponse> mediaById = mediaSearchItemAssembler.fromImported(media)
                .stream()
                .collect(Collectors.toMap(MediaSearchItemResponse::id, item -> item));
        List<ReviewResponse> reviewResponses = responses(reviews.getContent(), userId);
        List<ReviewWithMediaResponse> items = reviewResponses.stream()
                .map(review -> new ReviewWithMediaResponse(review, mediaById.get(review.mediaId())))
                .toList();
        return new PageResponse<>(items, reviews.getNumber(), reviews.getSize(),
                reviews.getTotalElements(), reviews.getTotalPages());
    }

    @Transactional(readOnly = true)
    public Optional<ReviewResponse> findByUserAndMedia(
            String username,
            UUID mediaId,
            UUID viewerId
    ) {
        User owner = userRepository.findByUsernameIgnoreCase(username.trim())
                .filter(user -> Boolean.TRUE.equals(user.getActive()))
                .filter(user -> socialAccessPolicy == null
                        ? viewerId != null && viewerId.equals(user.getId())
                            || user.getProfileVisibility() == null
                            || user.getProfileVisibility() == Visibility.PUBLIC
                        : socialAccessPolicy.canViewProfile(user, viewerId))
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "USER_PROFILE_NOT_FOUND",
                        "Perfil não encontrado"
                ));

        return reviewRepository.findByUserIdAndMediaId(owner.getId(), mediaId)
                .filter(this::hasContent)
                .filter(review -> socialAccessPolicy == null
                        ? viewerId != null && viewerId.equals(owner.getId())
                            || review.getVisibility() == Visibility.PUBLIC
                        : socialAccessPolicy.canViewContent(
                                owner.getId(), viewerId, review.getVisibility()))
                .map(review -> response(review, viewerId));
    }

    @Transactional
    public ReviewBackdropSelectionResponse updateBackdrop(UUID userId, UUID reviewId, String backdropKey) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "REVIEW_NOT_FOUND", "Review não encontrada"));
        if (!review.getUser().getId().equals(userId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "REVIEW_NOT_FOUND", "Review não encontrada");
        }

        ArtworkOptionResponse selected = userMediaArtworkService.selectReviewBackdrop(
                userId, review.getMedia().getId(), backdropKey);
        review.setBackdropKey(selected == null ? null : selected.key());
        review.setBackdropUrl(selected == null ? null : selected.url());
        Review saved = reviewRepository.saveAndFlush(review);
        return new ReviewBackdropSelectionResponse(
                saved.getBackdropKey(),
                saved.getBackdropUrl()
        );
    }

    @Transactional(readOnly = true)
    public ReviewResponse findPublicById(UUID userId, UUID reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .filter(this::hasContent)
                .filter(candidate -> userId == null
                        ? candidate.getVisibility() == Visibility.PUBLIC
                        : socialAccessPolicy == null
                            ? candidate.getVisibility() == Visibility.PUBLIC
                            : socialAccessPolicy.canViewContent(
                                    candidate.getUser().getId(), userId, candidate.getVisibility()))
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "REVIEW_NOT_FOUND", "Review não encontrada"));
        return response(review, userId);
    }

    @Transactional
    public ReviewResponse upsert(UUID userId, UUID mediaId, UpsertReviewRequest request) {
        BigDecimal requestedRating = request.rating() == null
                ? null
                : RatingValue.normalize(request.rating());
        validateVisibility(request.visibility());
        String content = normalizeContent(request.content());
        String richContent = request.richContent() == null ? null : request.richContent().toString();
        if (request.richContent() != null) {
            String richPlainText = RichTextDocument.validateAndExtractText(request.richContent(), false);
            if (!richPlainText.equals(content)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "RICH_TEXT_MISMATCH", "O texto simples deve corresponder ao documento formatado");
            }
        }

        User user = findUser(userId);
        Media media = findMedia(mediaId);
        mediaConsumptionPolicy.ensureReleased(media);
        Review review = reviewRepository.findByUserIdAndMediaId(userId, mediaId)
                .orElseGet(() -> newReview(user, media));
        boolean isNewReview = review.getId() == null;

        Rating rating = review.getRatingEntity();
        boolean ratingAlreadyExisted = rating != null;
        if (requestedRating != null) {
            if (rating == null) {
                Optional<Rating> existingRating = ratingRepository.findByUserIdAndMediaId(userId, mediaId);
                ratingAlreadyExisted = existingRating.isPresent();
                rating = existingRating.orElseGet(() -> newRating(user, media));
            }
            rating.setValue(requestedRating);
            rating.setRatedAt(Instant.now());
            review.setRatingEntity(rating);
        }
        review.setContent(content);
        review.setRichContent(richContent);
        String requestedBackdropKey = request.backdropKey() == null
                ? null : request.backdropKey().trim();
        if (requestedBackdropKey == null || requestedBackdropKey.isEmpty()) {
            review.setBackdropKey(null);
            review.setBackdropUrl(null);
        } else if (!Objects.equals(review.getBackdropKey(), requestedBackdropKey)) {
            ArtworkOptionResponse selected = userMediaArtworkService.selectReviewBackdrop(
                    userId, mediaId, requestedBackdropKey);
            review.setBackdropKey(selected.key());
            review.setBackdropUrl(selected.url());
        }
        review.setContainsSpoilers(Boolean.TRUE.equals(request.containsSpoilers()));
        review.setVisibility(request.visibility());
        UserMediaActivity previousActivity = review.getActivity();
        UserMediaActivity activity = request.activityId() == null
                ? previousActivity
                : findOwnedDiaryActivity(userId, mediaId, request.activityId());
        if (previousActivity != null
                && !Objects.equals(previousActivity.getId(), activity.getId())) {
            previousActivity.setReviewContent(null);
            previousActivity.setContainsSpoilers(false);
            userMediaActivityRepository.save(previousActivity);
        }
        if (activity != null) {
            review.setActivity(activity);
            activity.setRating(review.getRating());
            activity.setReviewContent(content);
            activity.setContainsSpoilers(review.getContainsSpoilers());
            activity.setVisibility(review.getVisibility());
            userMediaActivityRepository.save(activity);
        }
        if (review.getPublishedAt() == null) review.setPublishedAt(Instant.now());

        Review saved = reviewRepository.saveAndFlush(review);
        domainOutboxPublisher.publishMediaEvent(
                isNewReview ? DomainEventType.REVIEW_CREATED : DomainEventType.REVIEW_UPDATED,
                mediaId,
                Map.of("userId", userId.toString(), "reviewId", saved.getId().toString()));
        if (requestedRating != null) {
            domainOutboxPublisher.publishMediaEvent(
                    ratingAlreadyExisted ? DomainEventType.RATING_UPDATED : DomainEventType.RATING_CREATED,
                    mediaId,
                    Map.of("userId", userId.toString(), "ratingId", saved.getRatingEntity().getId().toString()));
        }
        Instant occurredAt = saved.getUpdatedAt() == null
                ? saved.getPublishedAt() == null ? Instant.now() : saved.getPublishedAt()
                : saved.getUpdatedAt();
        userFeedService.record(user, media, FeedActionType.REVIEWED, occurredAt, saved.getVisibility(),
                saved.getRating(), saved.getContent(), Boolean.TRUE.equals(saved.getContainsSpoilers()));
        if (requestedRating != null) {
            userFeedService.record(user, media, FeedActionType.RATED, Instant.now(), saved.getVisibility(),
                    requestedRating, null, false);
        }
        if (media.getType() != MediaType.TRACK && media.getType() != MediaType.EPISODE) {
            userMediaService.markCompleted(user, media);
        }
        if (isNewReview && media.getType() != MediaType.EPISODE) {
            mediaLikeService.like(userId, mediaId);
        }
        communityCacheInvalidator.evict(media);
        return response(saved, userId);
    }

    @Transactional
    public ReviewResponse upsertHQ(UUID userId, UUID profileId, UUID mediaId, UpsertReviewRequest request) {
        profileService.ensureCanManageHQ(profileId, userId);
        String content = normalizeContent(request.content());
        String richContent = request.richContent() == null ? null : request.richContent().toString();
        if (request.richContent() != null && !content.equals(RichTextDocument.validateAndExtractText(request.richContent(), true))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "RICH_TEXT_MISMATCH", "O texto simples deve corresponder ao documento formatado");
        }
        User user = findUser(userId);
        Media media = findMedia(mediaId);
        mediaConsumptionPolicy.ensureReleased(media);
        Profile profile = profileRepository.findById(profileId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND", "Perfil HQ não encontrado"));
        Review review = reviewRepository.findByAuthorProfileIdAndMediaId(profileId, mediaId).orElseGet(() -> {
            Review created = new Review(); created.setUser(user); created.setAuthorProfile(profile); created.setMedia(media); return created;
        });
        boolean isNewReview = review.getId() == null;
        review.setContent(content);
        review.setRichContent(richContent);
        review.setContainsSpoilers(Boolean.TRUE.equals(request.containsSpoilers()));
        review.setVisibility(com.scriptles.cabinet.user.enums.Visibility.PUBLIC);
        Review saved = reviewRepository.saveAndFlush(review);
        domainOutboxPublisher.publishMediaEvent(
                isNewReview ? DomainEventType.REVIEW_CREATED : DomainEventType.REVIEW_UPDATED,
                mediaId,
                Map.of("userId", userId.toString(), "reviewId", saved.getId().toString()));
        return response(saved, userId);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> findHQ(UUID viewerId, UUID profileId, int page, int size) {
        var result = reviewRepository.findAllByAuthorProfileIdAndContentIsNotNullOrderByPublishedAtDesc(
                profileId, PageRequest.of(page, size));
        return PageResponse.from(result.map(review -> response(review, viewerId)));
    }

    @Transactional
    public void delete(UUID userId, UUID mediaId) {
        reviewRepository.findByUserIdAndMediaId(userId, mediaId)
                .ifPresent(review -> {
                    UserMediaActivity activity = review.getActivity();
                    if (activity != null) {
                        // A diary log is an independent record. Removing its
                        // review must leave the log in place, but clear the
                        // synchronized review fields and detach the relation.
                        activity.setReviewContent(null);
                        activity.setContainsSpoilers(false);
                        review.setActivity(null);
                        userMediaActivityRepository.save(activity);
                    }
                    reviewLikeRepository.deleteByReviewId(review.getId());
                    userFeedService.remove(userId, mediaId, FeedActionType.REVIEWED);
                    domainOutboxPublisher.publishMediaEvent(DomainEventType.REVIEW_REMOVED, mediaId,
                            Map.of("userId", userId.toString(), "reviewId", review.getId().toString()));
                    reviewRepository.delete(review);
                });
    }

    private Review newReview(User user, Media media) {
        Review review = new Review();
        review.setUser(user);
        review.setMedia(media);
        ratingRepository.findByUserIdAndMediaId(user.getId(), media.getId())
                .ifPresent(review::setRatingEntity);
        return review;
    }

    private Rating newRating(User user, Media media) {
        Rating rating = new Rating();
        rating.setUser(user);
        rating.setMedia(media);
        rating.setVisibility(Visibility.PUBLIC);
        return rating;
    }

    private void validateVisibility(Visibility visibility) {
        if (visibility != Visibility.PUBLIC
                && visibility != Visibility.FOLLOWERS
                && visibility != Visibility.PRIVATE) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_REVIEW_VISIBILITY",
                    "A review deve ser pública ou privada"
            );
        }
    }

    private String normalizeContent(String content) {
        if (content == null || content.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REVIEW_CONTENT_REQUIRED",
                    "A resenha precisa ter conteúdo");
        }
        return content.trim();
    }

    private boolean hasContent(Review review) {
        return review.getContent() != null && !review.getContent().isBlank();
    }

    private UserMediaActivity findOwnedDiaryActivity(UUID userId, UUID mediaId, UUID activityId) {
        UserMediaActivity activity = userMediaActivityRepository.findByIdAndUserId(activityId, userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "DIARY_ENTRY_NOT_FOUND", "Registro do diário não encontrado"));
        if (!activity.getMedia().getId().equals(mediaId) || !isDiaryType(activity.getType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DIARY_ENTRY",
                    "O registro não pertence a esta obra ou não é uma entrada do diário");
        }
        return activity;
    }

    private boolean isDiaryType(ProfileActivityType type) {
        return type == ProfileActivityType.LOGGED
                || type == ProfileActivityType.RELOGGED
                || type == ProfileActivityType.WATCHED
                || type == ProfileActivityType.REWATCHED;
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "USER_NOT_FOUND",
                "Usuário não encontrado"
        ));
    }

    private Media findMedia(UUID mediaId) {
        return mediaRepository.findById(mediaId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "MEDIA_NOT_FOUND",
                "Mídia não encontrada"
        ));
    }

    private void validateMediaExists(UUID mediaId) {
        if (!mediaRepository.existsById(mediaId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Mídia não encontrada");
        }
    }

    private ReviewResponse response(Review review, UUID userId) {
        UUID reviewId = review.getId();
        return ReviewResponse.from(
                review,
                reviewLikeRepository.countByReviewId(reviewId),
                userId != null && reviewLikeRepository.existsByUserIdAndReviewId(userId, reviewId),
                reviewId == null
                        ? List.of()
                        : recentLikers(List.of(reviewId), userId).getOrDefault(reviewId, List.of()),
                mediaLikeRepository.existsByUserIdAndMediaId(review.getUser().getId(), review.getMedia().getId()),
                isReconsumed(review)
        );
    }

    private List<ReviewResponse> responses(List<Review> reviews, UUID userId) {
        if (reviews.isEmpty()) {
            return List.of();
        }

        List<UUID> reviewIds = reviews.stream().map(Review::getId).toList();
        Map<UUID, Long> likeCounts = reviewLikeRepository.countByReviewIds(reviewIds)
                .stream()
                .collect(Collectors.toMap(
                        ReviewLikeRepository.ReviewLikeCount::getReviewId,
                        ReviewLikeRepository.ReviewLikeCount::getLikeCount
                ));
        Set<UUID> likedReviewIds = userId == null
                ? Set.of()
                : Set.copyOf(reviewLikeRepository.findLikedReviewIds(userId, reviewIds));
        List<UUID> authorIds = reviews.stream().map(review -> review.getUser().getId()).distinct().toList();
        List<UUID> mediaIds = reviews.stream().map(review -> review.getMedia().getId()).distinct().toList();
        Set<MediaLikeKey> likedMediaByAuthors = Set.copyOf(
                mediaLikeRepository.findLikedPairs(authorIds, mediaIds).stream()
                        .map(pair -> new MediaLikeKey(pair.getUserId(), pair.getMediaId()))
                        .toList());
        Map<UUID, List<ReviewLikerResponse>> recentLikers = recentLikers(reviewIds, userId);

        return reviews.stream()
                .map(review -> ReviewResponse.from(
                        review,
                        likeCounts.getOrDefault(review.getId(), 0L),
                        likedReviewIds.contains(review.getId()),
                        recentLikers.getOrDefault(review.getId(), List.of()),
                        likedMediaByAuthors.contains(new MediaLikeKey(
                                review.getUser().getId(), review.getMedia().getId())),
                        isReconsumed(review)
                ))
                .toList();
    }

    private record MediaLikeKey(UUID userId, UUID mediaId) {}

    private boolean isReconsumed(Review review) {
        if (review.getActivity() == null) return false;
        ProfileActivityType type = review.getActivity().getType();
        return type == ProfileActivityType.RELOGGED || type == ProfileActivityType.REWATCHED;
    }

    private Map<UUID, List<ReviewLikerResponse>> recentLikers(
            List<UUID> reviewIds, UUID viewerId) {
        var rows = viewerId == null
                ? reviewLikeRepository.findRecentLikers(reviewIds)
                : reviewLikeRepository.findRecentLikersVisibleTo(reviewIds, viewerId);
        return rows
                .stream()
                .collect(Collectors.groupingBy(
                        liker -> UUID.fromString(liker.getReviewId()),
                        LinkedHashMap::new,
                        Collectors.mapping(
                                liker -> new ReviewLikerResponse(
                                        UUID.fromString(liker.getUserId()),
                                        liker.getUsername(),
                                        liker.getAvatarUrl(),
                                        "PRO".equals(liker.getAccountTier())
                                ),
                                Collectors.toList()
                        )
                ));
    }
}
