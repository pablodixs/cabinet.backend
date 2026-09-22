package com.scriptles.cabinet.user.importer;

import com.scriptles.cabinet.common.time.CabinetTime;
import com.scriptles.cabinet.lists.entity.MediaList;
import com.scriptles.cabinet.lists.entity.MediaListItem;
import com.scriptles.cabinet.lists.repository.MediaListItemRepository;
import com.scriptles.cabinet.lists.repository.MediaListRepository;
import com.scriptles.cabinet.media.dto.request.ImportExternalMediaRequest;
import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.Rating;
import com.scriptles.cabinet.media.entity.Review;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.repository.ExternalReferenceRepository;
import com.scriptles.cabinet.media.repository.MediaLikeRepository;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.media.repository.RatingRepository;
import com.scriptles.cabinet.media.repository.ReviewRepository;
import com.scriptles.cabinet.media.service.ExternalMediaService;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.entity.UserMedia;
import com.scriptles.cabinet.user.entity.UserMediaActivity;
import com.scriptles.cabinet.user.enums.ProfileActivityType;
import com.scriptles.cabinet.user.enums.UserMediaStatus;
import com.scriptles.cabinet.user.enums.Visibility;
import com.scriptles.cabinet.user.enums.FeedActionType;
import com.scriptles.cabinet.user.repository.UserMediaActivityRepository;
import com.scriptles.cabinet.user.repository.UserMediaRepository;
import com.scriptles.cabinet.user.service.UserFeedService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LetterboxdImportApplier {
    private final LetterboxdImportItemRepository itemRepository;
    private final MediaRepository mediaRepository;
    private final ExternalReferenceRepository externalReferenceRepository;
    private final ExternalMediaService externalMediaService;
    private final UserMediaRepository userMediaRepository;
    private final UserMediaActivityRepository activityRepository;
    private final RatingRepository ratingRepository;
    private final ReviewRepository reviewRepository;
    private final MediaLikeRepository mediaLikeRepository;
    private final MediaListRepository listRepository;
    private final MediaListItemRepository listItemRepository;
    private final ObjectMapper objectMapper;
    private final UserFeedService userFeedService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LetterboxdImportItemState apply(UUID itemId) {
        LetterboxdImportItem item = itemRepository.findById(itemId).orElseThrow();
        if (item.getState() == LetterboxdImportItemState.IMPORTED
                || item.getState() == LetterboxdImportItemState.PRESERVED
                || item.getState() == LetterboxdImportItemState.SKIPPED) {
            return item.getState();
        }
        Media media = resolveMedia(item);
        User user = item.getJob().getUser();
        LetterboxdItemPayload payload = objectMapper.readValue(item.getPayload(), LetterboxdItemPayload.class);
        boolean preserved = false;
        UserMediaActivity latestReviewActivity = null;

        addLetterboxdReference(media, item.getLetterboxdUri());
        UserMedia existingEntry = userMediaRepository.findByUserIdAndMediaId(user.getId(), media.getId()).orElse(null);
        boolean importedWatchlist = false;
        if (existingEntry == null && (payload.watched() || payload.watchlist())) {
            UserMedia entry = new UserMedia();
            entry.setUser(user);
            entry.setMedia(media);
            entry.setFavorite(false);
            entry.setPrivateEntry(false);
            applyStatus(entry, payload);
            userMediaRepository.save(entry);
            importedWatchlist = payload.watchlist() && !payload.watched();
        } else if (existingEntry != null && item.isOverrideStatus() && (payload.watched() || payload.watchlist())) {
            applyStatus(existingEntry, payload);
            userMediaRepository.save(existingEntry);
            importedWatchlist = payload.watchlist() && !payload.watched();
        } else if (existingEntry != null && (payload.watched() || payload.watchlist())) {
            preserved = true;
        }
        if (importedWatchlist) {
            userFeedService.record(user, media, FeedActionType.ADDED_TO_WATCHLIST,
                    toInstant(payload.watchlistAddedOn()), Visibility.PUBLIC, null, null, false);
        }

        for (LetterboxdItemPayload.Activity source : payload.activities()) {
            if (source.occurredOn() == null) continue;
            if (activityRepository.findByUserIdAndSourceAndSourceKey(
                    user.getId(), ExternalSource.LETTERBOXD, source.sourceKey()).isPresent()) continue;
            UserMediaActivity activity = new UserMediaActivity();
            activity.setUser(user);
            activity.setMedia(media);
            activity.setType(source.markedWatchedOnly()
                    ? ProfileActivityType.MARKED_WATCHED
                    : source.rewatch() ? ProfileActivityType.REWATCHED : ProfileActivityType.WATCHED);
            activity.setOccurredOn(source.occurredOn());
            activity.setLoggedOn(source.loggedOn());
            activity.setRating(source.rating());
            activity.setReviewContent(source.review());
            activity.setContainsSpoilers(false);
            activity.setTags(new LinkedHashSet<>(source.tags()));
            activity.setVisibility(Visibility.PUBLIC);
            activity.setSource(ExternalSource.LETTERBOXD);
            activity.setSourceKey(source.sourceKey());
            activity = activityRepository.save(activity);
            if (source.review() != null && !source.review().isBlank()
                    && (latestReviewActivity == null
                    || activity.getOccurredOn().isAfter(latestReviewActivity.getOccurredOn()))) {
                latestReviewActivity = activity;
            }
        }

        Rating rating = ratingRepository.findByUserIdAndMediaId(user.getId(), media.getId()).orElse(null);
        if (payload.rating() != null && (rating == null || item.isOverrideRating())) {
            if (rating == null) {
                rating = new Rating();
                rating.setUser(user);
                rating.setMedia(media);
                rating.setVisibility(Visibility.PUBLIC);
            }
            rating.setValue(payload.rating());
            rating.setRatedAt(toInstant(payload.ratingOn()));
            rating = ratingRepository.save(rating);
            userFeedService.record(user, media, FeedActionType.RATED, rating.getRatedAt(), rating.getVisibility(),
                    rating.getValue(), null, false);
        } else if (rating != null && payload.rating() != null) {
            preserved = true;
        }

        Review review = reviewRepository.findByUserIdAndMediaId(user.getId(), media.getId()).orElse(null);
        if (payload.review() != null && (review == null || item.isOverrideReview())) {
            if (review == null) {
                review = new Review();
                review.setUser(user);
                review.setMedia(media);
            }
            review.setRatingEntity(rating);
            review.setActivity(latestReviewActivity);
            review.setContent(payload.review());
            review.setContainsSpoilers(false);
            review.setVisibility(Visibility.PUBLIC);
            if (review.getPublishedAt() == null || item.isOverrideReview()) {
                review.setPublishedAt(toInstant(payload.reviewOn()));
            }
            reviewRepository.save(review);
            userFeedService.record(user, media, FeedActionType.REVIEWED, review.getPublishedAt(),
                    review.getVisibility(), review.getRating(), review.getContent(), false);
        } else if (review != null && payload.review() != null) {
            preserved = true;
        }

        if (payload.liked()) {
            Instant likedAt = toInstant(payload.likedOn());
            if (mediaLikeRepository.insertIfAbsentAt(UUID.randomUUID(), user.getId(), media.getId(), likedAt) > 0) {
                userFeedService.record(user, media, FeedActionType.LIKED, likedAt, Visibility.PUBLIC,
                        null, null, false);
            }
        }
        for (LetterboxdItemPayload.ListMembership membership : payload.lists()) {
            MediaList list = listRepository.findByOwnerIdAndOriginSourceAndOriginKey(
                    user.getId(), ExternalSource.LETTERBOXD, membership.sourceKey()).orElseGet(() -> {
                MediaList created = new MediaList();
                created.setOwner(user);
                created.setName(limit(membership.name(), 120));
                created.setVisibility(Visibility.PRIVATE);
                created.setOrdered(true);
                created.setOriginSource(ExternalSource.LETTERBOXD);
                created.setOriginKey(membership.sourceKey());
                return listRepository.save(created);
            });
            if (!listItemRepository.existsByListIdAndMediaId(list.getId(), media.getId())) {
                MediaListItem listItem = new MediaListItem();
                listItem.setList(list);
                listItem.setMedia(media);
                listItem.setPosition(membership.position());
                listItem.setNotes(membership.notes());
                listItemRepository.save(listItem);
            }
        }

        item.setState(preserved ? LetterboxdImportItemState.PRESERVED : LetterboxdImportItemState.IMPORTED);
        item.setSelectedMedia(media);
        item.setErrorMessage(null);
        itemRepository.save(item);
        return item.getState();
    }

    private Media resolveMedia(LetterboxdImportItem item) {
        if (item.getSelectedMedia() != null) return item.getSelectedMedia();
        var imported = externalMediaService.importMedia(new ImportExternalMediaRequest(
                ExternalSource.TMDB, item.getSelectedTmdbId(), MediaType.MOVIE));
        return mediaRepository.findById(imported.id()).orElseThrow();
    }

    private void addLetterboxdReference(Media media, String uri) {
        if (uri == null || externalReferenceRepository.findByMediaIdAndSource(
                media.getId(), ExternalSource.LETTERBOXD).isPresent()) return;
        if (externalReferenceRepository.existsBySourceAndExternalId(ExternalSource.LETTERBOXD, uri)) return;
        ExternalReference reference = new ExternalReference();
        reference.setMedia(media);
        reference.setSource(ExternalSource.LETTERBOXD);
        reference.setExternalId(uri);
        reference.setExternalUrl(uri);
        reference.setPrimaryReference(false);
        reference.setLastSyncedAt(Instant.now());
        externalReferenceRepository.save(reference);
    }

    private void applyStatus(UserMedia entry, LetterboxdItemPayload payload) {
        LocalDate date = latestActivityDate(payload);
        Instant instant = date.atStartOfDay(ZoneId.of("America/Sao_Paulo")).toInstant();
        if (payload.watched()) {
            entry.setStatus(UserMediaStatus.COMPLETED);
            entry.setCompletedAt(instant);
        } else {
            entry.setStatus(UserMediaStatus.PLANNED);
            entry.setCompletedAt(null);
        }
        entry.setLastInteractionAt(instant);
    }

    private LocalDate latestActivityDate(LetterboxdItemPayload payload) {
        return payload.activities().stream()
                .map(LetterboxdItemPayload.Activity::occurredOn)
                .filter(java.util.Objects::nonNull)
                .max(LocalDate::compareTo)
                .orElseGet(() -> payload.watchedMarkedOn() != null ? payload.watchedMarkedOn()
                        : payload.watchlistAddedOn() != null ? payload.watchlistAddedOn() : CabinetTime.today());
    }

    private String limit(String value, int length) {
        String normalized = value == null || value.isBlank() ? "Lista do Letterboxd" : value.trim();
        return normalized.length() <= length ? normalized : normalized.substring(0, length);
    }

    private Instant toInstant(LocalDate date) {
        LocalDate value = date == null ? CabinetTime.today() : date;
        return value.atStartOfDay(ZoneId.of("America/Sao_Paulo")).toInstant();
    }
}
