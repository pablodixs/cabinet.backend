package com.scriptles.cabinet.user.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.common.time.CabinetTime;
import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.Rating;
import com.scriptles.cabinet.media.entity.Review;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.repository.ExternalReferenceRepository;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.media.repository.RatingRepository;
import com.scriptles.cabinet.media.repository.ReviewRepository;
import com.scriptles.cabinet.media.service.MediaConsumptionPolicy;
import com.scriptles.cabinet.media.validation.RatingValue;
import com.scriptles.cabinet.user.dto.request.CreateDiaryEntryRequest;
import com.scriptles.cabinet.user.dto.response.DiaryEntryResponse;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.entity.UserMediaActivity;
import com.scriptles.cabinet.user.enums.ProfileActivityType;
import com.scriptles.cabinet.user.enums.Visibility;
import com.scriptles.cabinet.user.repository.UserMediaActivityRepository;
import com.scriptles.cabinet.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
    private final ExternalReferenceRepository externalReferenceRepository;
    private final MediaConsumptionPolicy mediaConsumptionPolicy;
    private final UserMediaService userMediaService;
    private final EpisodeTrackingService episodeTrackingService;
    private final SocialAccessPolicy socialAccessPolicy;

    @Transactional
    public DiaryEntryResponse create(UUID userId, CreateDiaryEntryRequest request) {
        User user = findUser(userId);
        Media media = mediaRepository.findById(request.mediaId()).orElseThrow(() ->
                notFound("MEDIA_NOT_FOUND", "Mídia não encontrada"));
        mediaConsumptionPolicy.ensureReleased(media);
        validate(request);

        Rating rating = upsertCanonicalRating(user, media, request.rating());
        String reviewContent = normalizeReview(request.review());

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
        activity = activityRepository.saveAndFlush(activity);

        if (reviewContent != null) {
            upsertCanonicalReview(user, media, rating, activity, reviewContent,
                    activity.getContainsSpoilers(), request.visibility());
        }
        if (media.getType() == MediaType.EPISODE) {
            episodeTrackingService.markWatched(userId, media.getId(), false);
        } else {
            userMediaService.markCompleted(user, media, false);
        }
        return DiaryEntryResponse.from(activity, findReference(media.getId()));
    }

    @Transactional(readOnly = true)
    public PageResponse<DiaryEntryResponse> findMine(UUID userId, int page, int size) {
        findUser(userId);
        return findEntries(userId, true, false, page, size);
    }

    @Transactional(readOnly = true)
    public PageResponse<DiaryEntryResponse> findByUsername(
            String username,
            UUID viewerId,
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
        return findEntries(user.getId(), ownProfile, followerAccess, page, size);
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

    private PageResponse<DiaryEntryResponse> findEntries(
            UUID userId,
            boolean includePrivate,
            boolean includeFollowers,
            int page,
            int size
    ) {
        Page<UserMediaActivity> entries = includeFollowers && !includePrivate
                ? activityRepository.findDiaryEntriesVisibleToFollower(
                        userId, DIARY_TYPES, List.of(Visibility.PUBLIC, Visibility.FOLLOWERS),
                        PageRequest.of(page, size))
                : activityRepository.findDiaryEntries(
                        userId, DIARY_TYPES, includePrivate, Visibility.PUBLIC,
                        PageRequest.of(page, size));
        Map<UUID, ExternalReference> references = findReferences(entries);
        return PageResponse.from(entries.map(entry -> DiaryEntryResponse.from(
                entry, references.get(entry.getMedia().getId()))));
    }

    private Rating upsertCanonicalRating(User user, Media media, java.math.BigDecimal value) {
        if (value == null) {
            return ratingRepository.findByUserIdAndMediaId(user.getId(), media.getId()).orElse(null);
        }
        java.math.BigDecimal normalized = RatingValue.normalize(value);
        Rating rating = ratingRepository.findByUserIdAndMediaId(user.getId(), media.getId())
                .orElseGet(() -> {
                    Rating created = new Rating();
                    created.setUser(user);
                    created.setMedia(media);
                    created.setVisibility(Visibility.PUBLIC);
                    return created;
                });
        rating.setValue(normalized);
        rating.setRatedAt(Instant.now());
        return ratingRepository.save(rating);
    }

    private void upsertCanonicalReview(
            User user,
            Media media,
            Rating rating,
            UserMediaActivity activity,
            String content,
            boolean containsSpoilers,
            Visibility visibility
    ) {
        if (media.getType() == MediaType.TRACK || media.getType() == MediaType.EPISODE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REVIEW_NOT_SUPPORTED",
                    "Faixas e episódios aceitam somente nota");
        }
        Review review = reviewRepository.findByUserIdAndMediaId(user.getId(), media.getId())
                .orElseGet(() -> {
                    Review created = new Review();
                    created.setUser(user);
                    created.setMedia(media);
                    created.setPublishedAt(Instant.now());
                    return created;
                });
        review.setRatingEntity(rating);
        review.setActivity(activity);
        review.setContent(content);
        review.setContainsSpoilers(containsSpoilers);
        review.setVisibility(visibility);
        reviewRepository.save(review);
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
