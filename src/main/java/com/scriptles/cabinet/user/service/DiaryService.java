package com.scriptles.cabinet.user.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.common.api.RichTextDocument;
import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.common.time.CabinetTime;
import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.Rating;
import com.scriptles.cabinet.media.entity.Review;
import com.scriptles.cabinet.media.dto.response.ArtworkOptionResponse;
import com.scriptles.cabinet.media.service.UserMediaArtworkService;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.repository.ExternalReferenceRepository;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.media.repository.RatingRepository;
import com.scriptles.cabinet.media.repository.ReviewLikeRepository;
import com.scriptles.cabinet.media.repository.ReviewRepository;
import com.scriptles.cabinet.media.service.MediaConsumptionPolicy;
import com.scriptles.cabinet.media.service.MediaCommunityCacheInvalidator;
import com.scriptles.cabinet.media.service.MediaLikeService;
import com.scriptles.cabinet.media.service.UserArtworkResolver;
import com.scriptles.cabinet.media.validation.RatingValue;
import com.scriptles.cabinet.user.dto.request.CreateDiaryEntryRequest;
import com.scriptles.cabinet.user.dto.request.UpdateDiaryEntryRequest;
import com.scriptles.cabinet.user.dto.response.DiaryEntryResponse;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.entity.UserMediaActivity;
import com.scriptles.cabinet.user.enums.ProfileActivityType;
import com.scriptles.cabinet.user.enums.FeedActionType;
import com.scriptles.cabinet.user.enums.Visibility;
import com.scriptles.cabinet.user.repository.UserMediaActivityRepository;
import com.scriptles.cabinet.user.repository.UserRepository;
import com.scriptles.cabinet.user.service.UserFeedService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DiaryService {
    private static final EnumSet<ProfileActivityType> DIARY_TYPES = EnumSet.of(
            ProfileActivityType.LOGGED,
            ProfileActivityType.RELOGGED,
            ProfileActivityType.WATCHED,
            ProfileActivityType.REWATCHED
    );

    private final UserMediaActivityRepository activityRepository;
    private final UserRepository userRepository;
    private final MediaRepository mediaRepository;
    private final RatingRepository ratingRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewLikeRepository reviewLikeRepository;
    private final ExternalReferenceRepository externalReferenceRepository;
    private final MediaConsumptionPolicy mediaConsumptionPolicy;
    private final MediaCommunityCacheInvalidator communityCacheInvalidator;
    private final MediaLikeService mediaLikeService;
    private final UserMediaService userMediaService;
    private final EpisodeTrackingService episodeTrackingService;
    private final SocialAccessPolicy socialAccessPolicy;
    private final UserArtworkResolver userArtworkResolver;
    private final UserTagService userTagService;
    private final UserFeedService userFeedService;
    private final UserMediaArtworkService userMediaArtworkService;

    @Transactional
    public DiaryEntryResponse create(UUID userId, CreateDiaryEntryRequest request) {
        User user = findUser(userId);
        Media media = mediaRepository.findById(request.mediaId()).orElseThrow(() ->
                notFound("MEDIA_NOT_FOUND", "Mídia não encontrada"));
        mediaConsumptionPolicy.ensureReleased(media);
        validate(request);

        Rating rating = upsertCanonicalRating(user, media, request.rating(), request.visibility());
        String reviewContent = normalizeReview(request.review());
        if (request.richContent() != null) validateRichContent(reviewContent, request.richContent());

        UserMediaActivity activity = new UserMediaActivity();
        activity.setUser(user);
        activity.setMedia(media);
        activity.setType(request.reconsumption()
                ? ProfileActivityType.RELOGGED
                : ProfileActivityType.LOGGED);
        activity.setOccurredOn(request.occurredOn());
        activity.setLoggedOn(CabinetTime.today());
        activity.setRating(request.rating() == null ? null : rating.getValue());
        activity.setReviewContent(reviewContent);
        activity.setContainsSpoilers(reviewContent != null && Boolean.TRUE.equals(request.containsSpoilers()));
        activity.setVisibility(request.visibility());
        activity.setSource(ExternalSource.MANUAL);
        activity.setSourceKey("cabinet:diary:" + UUID.randomUUID());
        activity.setTags(normalizeTags(request.tags()));
        userTagService.ensureTags(user, activity.getTags());
        activity = activityRepository.saveAndFlush(activity);

        if (reviewContent != null) {
            upsertCanonicalReview(user, media, rating, activity, reviewContent,
                    activity.getContainsSpoilers(), request.visibility(), request.backdropKey(), request.richContent() == null ? null : request.richContent().toString());
        }
        if (media.getType() != MediaType.EPISODE) {
            mediaLikeService.like(userId, media.getId());
        }
        if (media.getType() == MediaType.EPISODE) {
            episodeTrackingService.markWatched(userId, media.getId(), false);
        } else {
            userMediaService.markCompleted(user, media, false);
        }
        communityCacheInvalidator.evict(media);
        return DiaryEntryResponse.from(
                activity,
                findReference(media.getId()),
                resolveArtwork(userId, List.of(media)).get(media.getId()).coverUrl(),
                request.richContent()
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<DiaryEntryResponse> findMine(UUID userId, int page, int size) {
        findUser(userId);
        return findEntries(userId, true, false, null, page, size);
    }

    @Transactional(readOnly = true)
    public PageResponse<DiaryEntryResponse> findMine(UUID userId, MediaType mediaType, int page, int size) {
        findUser(userId);
        return findEntries(userId, true, false, mediaType, page, size);
    }

    @Transactional(readOnly = true)
    public PageResponse<DiaryEntryResponse> findByUsername(
            String username,
            UUID viewerId,
            int page,
            int size
    ) {
        return findByUsername(username, viewerId, null, page, size);
    }

    @Transactional(readOnly = true)
    public PageResponse<DiaryEntryResponse> findByUsername(
            String username,
            UUID viewerId,
            MediaType mediaType,
            int page,
            int size
    ) {
        User user = userRepository.findByUsernameIgnoreCase(username.trim())
                .filter(candidate -> Boolean.TRUE.equals(candidate.getActive()))
                .orElseThrow(() -> notFound("USER_PROFILE_NOT_FOUND", "Perfil não encontrado"));
        boolean ownProfile = viewerId != null && viewerId.equals(user.getId());
        boolean accessible = socialAccessPolicy == null
                ? ownProfile || user.getProfileVisibility() == Visibility.PUBLIC
                : socialAccessPolicy.canViewProfile(user, viewerId);
        if (!accessible) {
            throw notFound("USER_PROFILE_NOT_FOUND", "Perfil não encontrado");
        }
        boolean followerAccess = !ownProfile && viewerId != null
                && socialAccessPolicy != null
                && socialAccessPolicy.isAcceptedFollower(viewerId, user.getId());
        return findEntries(user.getId(), ownProfile, followerAccess, mediaType, page, size);
    }

    @Transactional
    public void delete(UUID userId, UUID entryId) {
        UserMediaActivity activity = activityRepository.findByIdAndUserId(entryId, userId)
                .orElseThrow(() -> notFound("DIARY_ENTRY_NOT_FOUND", "Registro do diário não encontrado"));
        if (!DIARY_TYPES.contains(activity.getType())) {
            throw notFound("DIARY_ENTRY_NOT_FOUND", "Registro do diário não encontrado");
        }
        reviewRepository.findByActivityId(entryId).ifPresent(review -> {
            review.setActivity(null);
            reviewRepository.saveAndFlush(review);
        });
        activityRepository.delete(activity);
    }

    @Transactional
    public DiaryEntryResponse update(UUID userId, UUID entryId, UpdateDiaryEntryRequest request) {
        UserMediaActivity activity = activityRepository.findByIdAndUserId(entryId, userId)
                .orElseThrow(() -> notFound("DIARY_ENTRY_NOT_FOUND", "Registro do diário não encontrado"));
        if (!DIARY_TYPES.contains(activity.getType())) {
            throw notFound("DIARY_ENTRY_NOT_FOUND", "Registro do diário não encontrado");
        }
        validate(request);

        User user = findUser(userId);
        Media media = activity.getMedia();
        mediaConsumptionPolicy.ensureReleased(media);
        Rating rating = upsertCanonicalRating(user, media, request.rating(), request.visibility());
        String reviewContent = normalizeReview(request.review());
        if (request.richContent() != null) validateRichContent(reviewContent, request.richContent());
        boolean createsReview = reviewContent != null
                && reviewRepository.findByUserIdAndMediaId(userId, media.getId()).isEmpty();

        activity.setType(request.reconsumption()
                ? reconsumedType(activity.getType())
                : loggedType(activity.getType()));
        activity.setOccurredOn(request.occurredOn());
        activity.setRating(request.rating() == null ? null : rating.getValue());
        activity.setReviewContent(reviewContent);
        activity.setContainsSpoilers(reviewContent != null && Boolean.TRUE.equals(request.containsSpoilers()));
        activity.setVisibility(request.visibility());
        activity.setTags(normalizeTags(request.tags()));
        userTagService.ensureTags(user, activity.getTags());
        activity = activityRepository.saveAndFlush(activity);

        Review review = reviewRepository.findByActivityId(entryId).orElse(null);
        if (reviewContent != null) {
            upsertCanonicalReview(user, media, rating, activity, reviewContent,
                    activity.getContainsSpoilers(), request.visibility(), request.backdropKey(), request.richContent() == null ? null : request.richContent().toString());
        } else if (review != null) {
            reviewLikeRepository.deleteByReviewId(review.getId());
            reviewLikeRepository.flush();
            reviewRepository.delete(review);
            reviewRepository.flush();
            userFeedService.remove(userId, media.getId(), FeedActionType.REVIEWED);
        }
        if (createsReview && media.getType() != MediaType.EPISODE) {
            mediaLikeService.like(userId, media.getId());
        }
        communityCacheInvalidator.evict(media);
        return DiaryEntryResponse.from(
                activity,
                findReference(media.getId()),
                resolveArtwork(userId, List.of(media)).get(media.getId()).coverUrl(),
                request.richContent()
        );
    }

    private PageResponse<DiaryEntryResponse> findEntries(
            UUID userId,
            boolean includePrivate,
            boolean includeFollowers,
            MediaType mediaType,
            int page,
            int size
    ) {
        Page<UserMediaActivity> entries = includeFollowers && !includePrivate
                ? activityRepository.findDiaryEntriesVisibleToFollower(
                        userId, DIARY_TYPES, mediaType, List.of(Visibility.PUBLIC, Visibility.FOLLOWERS),
                        PageRequest.of(page, size))
                : activityRepository.findDiaryEntries(
                        userId, DIARY_TYPES, mediaType, includePrivate, Visibility.PUBLIC,
                        PageRequest.of(page, size));
        Map<UUID, ExternalReference> references = findReferences(entries);
        Map<UUID, UserArtworkResolver.ResolvedArtwork> artworks = resolveArtwork(
                userId,
                entries.getContent().stream().map(UserMediaActivity::getMedia).toList()
        );
        List<UUID> activityIds = entries.getContent().stream().map(UserMediaActivity::getId).toList();
        List<Visibility> reviewVisibilities = includePrivate
                ? List.of(Visibility.PUBLIC, Visibility.FOLLOWERS, Visibility.PRIVATE)
                : includeFollowers ? List.of(Visibility.PUBLIC, Visibility.FOLLOWERS) : List.of(Visibility.PUBLIC);
        Map<UUID, JsonNode> richContentByActivityId = activityIds.isEmpty() ? Map.of()
                : reviewRepository.findDiaryRichContent(activityIds, reviewVisibilities).stream().collect(Collectors.toMap(
                        review -> review.getActivity().getId(),
                        review -> RichTextDocument.parse(review.getRichContent())
                ));
        return PageResponse.from(entries.map(entry -> DiaryEntryResponse.from(
                entry,
                references.get(entry.getMedia().getId()),
                artworks.get(entry.getMedia().getId()).coverUrl(),
                richContentByActivityId.get(entry.getId())
        )));
    }

    private Map<UUID, UserArtworkResolver.ResolvedArtwork> resolveArtwork(
            UUID ownerId,
            java.util.Collection<Media> mediaItems
    ) {
        if (userArtworkResolver != null) {
            return userArtworkResolver.resolve(ownerId, mediaItems);
        }
        return mediaItems.stream().collect(Collectors.toMap(
                Media::getId,
                media -> new UserArtworkResolver.ResolvedArtwork(
                        media.getCoverUrl(), media.getBackdropUrl(), false, false)
        ));
    }

    private Rating upsertCanonicalRating(User user, Media media, java.math.BigDecimal value, Visibility visibility) {
        if (value == null) {
            return ratingRepository.findByUserIdAndMediaId(user.getId(), media.getId()).orElse(null);
        }
        java.math.BigDecimal normalized = RatingValue.normalize(value);
        Rating rating = ratingRepository.findByUserIdAndMediaId(user.getId(), media.getId())
                .orElseGet(() -> {
                    Rating created = new Rating();
                    created.setUser(user);
                    created.setMedia(media);
                    created.setVisibility(visibility);
                    return created;
                });
        rating.setValue(normalized);
        rating.setVisibility(visibility);
        rating.setRatedAt(Instant.now());
        Rating saved = ratingRepository.save(rating);
        userFeedService.record(user, media, FeedActionType.RATED, saved.getRatedAt(), visibility,
                saved.getValue(), null, false);
        return saved;
    }

    private void upsertCanonicalReview(
            User user,
            Media media,
            Rating rating,
            UserMediaActivity activity,
            String content,
            boolean containsSpoilers,
            Visibility visibility,
            String backdropKey,
            String richContent
    ) {
        Review review = reviewRepository.findByUserIdAndMediaId(user.getId(), media.getId())
                .orElseGet(() -> {
                    Review created = new Review();
                    created.setUser(user);
                    created.setMedia(media);
                    created.setPublishedAt(Instant.now());
                    return created;
                });
        UserMediaActivity previousActivity = review.getActivity();
        if (previousActivity != null
                && !Objects.equals(previousActivity.getId(), activity.getId())) {
            previousActivity.setReviewContent(null);
            previousActivity.setContainsSpoilers(false);
            activityRepository.save(previousActivity);
        }
        review.setRatingEntity(rating);
        review.setActivity(activity);
        review.setContent(content);
        review.setRichContent(richContent);
        String requestedBackdropKey = backdropKey == null ? null : backdropKey.trim();
        if (requestedBackdropKey == null || requestedBackdropKey.isEmpty()) {
            review.setBackdropKey(null);
            review.setBackdropUrl(null);
        } else if (!Objects.equals(review.getBackdropKey(), requestedBackdropKey)) {
            ArtworkOptionResponse selected = userMediaArtworkService.selectReviewBackdrop(
                    user.getId(), media.getId(), requestedBackdropKey);
            review.setBackdropKey(selected.key());
            review.setBackdropUrl(selected.url());
        }
        review.setContainsSpoilers(containsSpoilers);
        review.setVisibility(visibility);
        reviewRepository.save(review);
        Instant occurredAt = activity.getOccurredOn()
                .atStartOfDay(ZoneId.of("America/Sao_Paulo")).toInstant();
        userFeedService.record(user, media, FeedActionType.REVIEWED, occurredAt, visibility,
                review.getRating(), content, containsSpoilers);
    }

    private void validateRichContent(String plain, tools.jackson.databind.JsonNode rich) {
        String extracted = RichTextDocument.validateAndExtractText(rich, false);
        if (!Objects.equals(plain, extracted)) throw new ApiException(HttpStatus.BAD_REQUEST,
                "RICH_TEXT_MISMATCH", "O texto simples deve corresponder ao documento formatado");
    }

    private void validate(CreateDiaryEntryRequest request) {
        if (request.occurredOn().isAfter(CabinetTime.today())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DIARY_DATE",
                    "A data do registro não pode estar no futuro");
        }
        if (request.visibility() != Visibility.PUBLIC
                && request.visibility() != Visibility.FOLLOWERS
                && request.visibility() != Visibility.PRIVATE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DIARY_VISIBILITY",
                    "O registro deve ser público ou privado");
        }
        if (request.rating() != null) RatingValue.normalize(request.rating());
    }

    private void validate(UpdateDiaryEntryRequest request) {
        if (request.occurredOn().isAfter(CabinetTime.today())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DIARY_DATE",
                    "A data do registro não pode estar no futuro");
        }
        if (request.visibility() != Visibility.PUBLIC
                && request.visibility() != Visibility.FOLLOWERS
                && request.visibility() != Visibility.PRIVATE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DIARY_VISIBILITY",
                    "O registro deve ser público ou privado");
        }
        if (request.rating() != null) RatingValue.normalize(request.rating());
    }

    private ProfileActivityType reconsumedType(ProfileActivityType current) {
        return current == ProfileActivityType.WATCHED || current == ProfileActivityType.REWATCHED
                ? ProfileActivityType.REWATCHED : ProfileActivityType.RELOGGED;
    }

    private ProfileActivityType loggedType(ProfileActivityType current) {
        return current == ProfileActivityType.WATCHED || current == ProfileActivityType.REWATCHED
                ? ProfileActivityType.WATCHED : ProfileActivityType.LOGGED;
    }

    private String normalizeReview(String review) {
        return review == null || review.isBlank() ? null : review.trim();
    }

    private LinkedHashSet<String> normalizeTags(java.util.Set<String> tags) {
        if (tags == null || tags.isEmpty()) return new LinkedHashSet<>();
        return tags.stream()
                .filter(tag -> tag != null && !tag.isBlank())
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Map<UUID, ExternalReference> findReferences(Page<UserMediaActivity> entries) {
        if (entries.isEmpty()) return Map.of();
        return externalReferenceRepository.findAllByMediaIdInAndPrimaryReferenceTrue(
                        entries.stream().map(entry -> entry.getMedia().getId()).toList())
                .stream()
                .collect(Collectors.toMap(
                        reference -> reference.getMedia().getId(),
                        Function.identity(),
                        (first, ignored) -> first));
    }

    private ExternalReference findReference(UUID mediaId) {
        return externalReferenceRepository.findAllByMediaIdInAndPrimaryReferenceTrue(java.util.List.of(mediaId))
                .stream().findFirst().orElse(null);
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() ->
                notFound("USER_NOT_FOUND", "Usuário não encontrado"));
    }

    private ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }
}
