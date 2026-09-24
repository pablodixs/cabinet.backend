package com.scriptles.cabinet.user.service;

import com.scriptles.cabinet.common.outbox.DomainEventType;
import com.scriptles.cabinet.common.outbox.DomainOutboxPublisher;
import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.event.SeriesTrackingRequestedEvent;
import com.scriptles.cabinet.media.repository.ExternalReferenceRepository;
import com.scriptles.cabinet.media.repository.MediaLikeRepository;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.media.repository.RatingRepository;
import com.scriptles.cabinet.media.repository.ReviewRepository;
import com.scriptles.cabinet.media.service.MediaConsumptionPolicy;
import com.scriptles.cabinet.media.service.MediaCreditService;
import com.scriptles.cabinet.media.service.UserArtworkResolver;
import com.scriptles.cabinet.user.dto.response.LibraryEntryResponse;
import com.scriptles.cabinet.user.dto.response.LibraryMediaResponse;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.entity.UserMedia;
import com.scriptles.cabinet.user.enums.UserMediaStatus;
import com.scriptles.cabinet.user.repository.UserMediaRepository;
import com.scriptles.cabinet.user.repository.UserMediaActivityRepository;
import com.scriptles.cabinet.user.entity.UserMediaActivity;
import com.scriptles.cabinet.user.enums.ProfileActivityType;
import com.scriptles.cabinet.user.enums.Visibility;
import com.scriptles.cabinet.user.enums.FeedActionType;
import com.scriptles.cabinet.common.time.CabinetTime;
import com.scriptles.cabinet.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserMediaService {
    private final UserMediaRepository userMediaRepository;
    private final UserMediaActivityRepository userMediaActivityRepository;
    private final UserRepository userRepository;
    private final MediaRepository mediaRepository;
    private final ExternalReferenceRepository externalReferenceRepository;
    private final MediaLikeRepository mediaLikeRepository;
    private final RatingRepository ratingRepository;
    private final ReviewRepository reviewRepository;
    private final MediaCreditService mediaCreditService;
    private final MediaConsumptionPolicy mediaConsumptionPolicy;
    private final ApplicationEventPublisher eventPublisher;
    private final UserArtworkResolver userArtworkResolver;
    private final UserFeedService userFeedService;
    private final InterestProfileCache interestProfileCache;
    private final DomainOutboxPublisher domainOutboxPublisher;

    @Transactional(readOnly = true)
    public PageResponse<LibraryMediaResponse> findLibrary(
            UUID userId,
            UserMediaStatus status,
            MediaType type,
            int page,
            int size
    ) {
        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "lastInteractionAt")
                        .and(Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        Page<UserMedia> entries = userMediaRepository.findLibrary(
                userId,
                status,
                type,
                pageable
        );
        Map<UUID, ExternalReference> referencesByMediaId = entries.isEmpty()
                ? Map.of()
                : externalReferenceRepository
                        .findAllByMediaIdInAndPrimaryReferenceTrue(
                                entries.getContent().stream()
                                        .map(entry -> entry.getMedia().getId())
                                        .toList()
                        )
                        .stream()
                        .collect(Collectors.toMap(
                                reference -> reference.getMedia().getId(),
                                Function.identity(),
                                (first, ignored) -> first
                        ));
        Map<UUID, UserArtworkResolver.ResolvedArtwork> artworks = resolveArtwork(
                userId, entries.getContent().stream().map(UserMedia::getMedia).toList());
        var mediaItems = entries.getContent().stream().map(UserMedia::getMedia).toList();
        var mediaIds = mediaItems.stream().map(Media::getId).toList();
        Set<UUID> likedMediaIds = mediaIds.isEmpty()
                ? Set.of()
                : Set.copyOf(mediaLikeRepository.findLikedMediaIds(userId, mediaIds));
        Map<UUID, java.math.BigDecimal> ratingsByMediaId = mediaIds.isEmpty()
                ? Map.of()
                : ratingRepository.findAllByUserIdAndMediaIdIn(userId, mediaIds).stream()
                        .collect(Collectors.toMap(
                                rating -> rating.getMedia().getId(),
                                rating -> rating.getValue()
                        ));
        Set<UUID> reviewedMediaIds = mediaIds.isEmpty()
                ? Set.of()
                : Set.copyOf(reviewRepository.findReviewedMediaIds(userId, mediaIds));
        Map<UUID, MediaCreditService.CreditSummary> creditsByMediaId =
                mediaCreditService.summaries(mediaItems);

        return PageResponse.from(entries.map(entry -> LibraryMediaResponse.from(
                entry,
                referencesByMediaId.get(entry.getMedia().getId()),
                artworks.get(entry.getMedia().getId()).coverUrl(),
                likedMediaIds.contains(entry.getMedia().getId()),
                ratingsByMediaId.get(entry.getMedia().getId()),
                reviewedMediaIds.contains(entry.getMedia().getId()),
                creditsByMediaId
                        .getOrDefault(entry.getMedia().getId(), MediaCreditService.CreditSummary.empty())
                        .creator()
        )));
    }

    private Map<UUID, UserArtworkResolver.ResolvedArtwork> resolveArtwork(
            UUID viewerId,
            java.util.Collection<Media> mediaItems
    ) {
        if (userArtworkResolver != null) return userArtworkResolver.resolve(viewerId, mediaItems);
        return mediaItems.stream().collect(Collectors.toMap(
                Media::getId,
                media -> new UserArtworkResolver.ResolvedArtwork(
                        media.getCoverUrl(), media.getBackdropUrl(), false, false)
        ));
    }

    @Transactional(readOnly = true)
    public Optional<LibraryEntryResponse> find(UUID userId, UUID mediaId) {
        return userMediaRepository.findByUserIdAndMediaId(userId, mediaId)
                .map(LibraryEntryResponse::from);
    }

    @Transactional
    @CacheEvict(cacheNames = "mediaCommunity", key = "#mediaId")
    public LibraryEntryResponse upsert(UUID userId, UUID mediaId, UserMediaStatus status) {
        interestProfileCache.invalidate(userId);
        User user = findUser(userId);
        Media media = findMedia(mediaId);
        UserMedia entry = userMediaRepository.findByUserIdAndMediaId(userId, mediaId)
                .orElseGet(() -> newEntry(user, media));
        UserMediaStatus previousStatus = entry.getStatus();
        boolean recordActivity = entry.getId() == null || entry.getStatus() != status;

        if (status != UserMediaStatus.PLANNED) {
            mediaConsumptionPolicy.ensureReleased(media);
        }
        applyStatus(entry, status, Instant.now());
        UserMedia saved = userMediaRepository.saveAndFlush(entry);
        publishCompletionTransition(userId, mediaId, previousStatus, status);
        if (recordActivity) recordActivity(saved, status);
        if (media.getType() == MediaType.SERIES && status == UserMediaStatus.IN_PROGRESS) {
            eventPublisher.publishEvent(new SeriesTrackingRequestedEvent(mediaId));
        }
        return LibraryEntryResponse.from(saved);
    }

    @Transactional
    @CacheEvict(cacheNames = "mediaCommunity", key = "#mediaId")
    public void delete(UUID userId, UUID mediaId) {
        interestProfileCache.invalidate(userId);
        userMediaRepository.findByUserIdAndMediaId(userId, mediaId)
                .ifPresent(entry -> {
                    if (entry.getStatus() == UserMediaStatus.COMPLETED) {
                        publishCompletionTransition(userId, mediaId,
                                UserMediaStatus.COMPLETED, UserMediaStatus.PLANNED);
                    }
                    userMediaRepository.delete(entry);
                });
        userFeedService.remove(userId, mediaId, FeedActionType.ADDED_TO_WATCHLIST);
    }

    @Transactional
    public UserMedia markCompleted(User user, Media media) {
        return markCompleted(user, media, true);
    }

    @Transactional
    public UserMedia markCompleted(User user, Media media, boolean recordCompletionActivity) {
        interestProfileCache.invalidate(user.getId());
        mediaConsumptionPolicy.ensureReleased(media);
        UserMedia entry = userMediaRepository.findByUserIdAndMediaId(user.getId(), media.getId())
                .orElseGet(() -> newEntry(user, media));
        UserMediaStatus previousStatus = entry.getStatus();
        boolean recordActivity = entry.getId() == null || entry.getStatus() != UserMediaStatus.COMPLETED;
        applyStatus(entry, UserMediaStatus.COMPLETED, Instant.now());
        UserMedia saved = userMediaRepository.save(entry);
        publishCompletionTransition(user.getId(), media.getId(), previousStatus, UserMediaStatus.COMPLETED);
        if (recordCompletionActivity && recordActivity) recordActivity(saved, UserMediaStatus.COMPLETED);
        return saved;
    }

    private void publishCompletionTransition(
            UUID userId,
            UUID mediaId,
            UserMediaStatus previousStatus,
            UserMediaStatus newStatus
    ) {
        if (previousStatus == UserMediaStatus.COMPLETED && newStatus != UserMediaStatus.COMPLETED) {
            domainOutboxPublisher.publishMediaEvent(DomainEventType.MEDIA_UNCOMPLETED, mediaId,
                    Map.of("userId", userId.toString()));
        } else if (newStatus == UserMediaStatus.COMPLETED && previousStatus != UserMediaStatus.COMPLETED) {
            domainOutboxPublisher.publishMediaEvent(DomainEventType.MEDIA_COMPLETED, mediaId,
                    Map.of("userId", userId.toString()));
        }
    }

    private UserMedia newEntry(User user, Media media) {
        UserMedia entry = new UserMedia();
        entry.setUser(user);
        entry.setMedia(media);
        entry.setFavorite(false);
        entry.setPrivateEntry(false);
        return entry;
    }

    private void applyStatus(UserMedia entry, UserMediaStatus status, Instant now) {
        validateStatus(entry.getMedia().getType(), status);
        UserMediaStatus previousStatus = entry.getStatus();

        switch (status) {
            case PLANNED -> {
                entry.setStartedAt(null);
                entry.setCompletedAt(null);
            }
            case IN_PROGRESS -> {
                if (entry.getStartedAt() == null) {
                    entry.setStartedAt(now);
                }
                entry.setCompletedAt(null);
            }
            case COMPLETED -> {
                if (previousStatus != UserMediaStatus.COMPLETED || entry.getCompletedAt() == null) {
                    entry.setCompletedAt(now);
                }
            }
            case PAUSED, DROPPED -> entry.setCompletedAt(null);
        }

        entry.setStatus(status);
        entry.setLastInteractionAt(now);
    }

    private void recordActivity(UserMedia entry, UserMediaStatus status) {
        UserMediaActivity activity = new UserMediaActivity();
        activity.setUser(entry.getUser());
        activity.setMedia(entry.getMedia());
        activity.setType(switch (status) {
            case PLANNED -> ProfileActivityType.ADDED_TO_LIBRARY;
            case IN_PROGRESS -> ProfileActivityType.STARTED;
            case COMPLETED -> ProfileActivityType.COMPLETED;
            case PAUSED -> ProfileActivityType.PAUSED;
            case DROPPED -> ProfileActivityType.DROPPED;
        });
        activity.setOccurredOn(CabinetTime.today());
        activity.setVisibility(Boolean.TRUE.equals(entry.getPrivateEntry()) ? Visibility.PRIVATE : Visibility.PUBLIC);
        activity.setSourceKey("cabinet:" + UUID.randomUUID());
        userMediaActivityRepository.save(activity);
        if (status == UserMediaStatus.PLANNED) {
            userFeedService.record(entry.getUser(), entry.getMedia(), FeedActionType.ADDED_TO_WATCHLIST,
                    entry.getLastInteractionAt(), activity.getVisibility(), null, null, false);
        }
    }

    private void validateStatus(MediaType mediaType, UserMediaStatus status) {
        EnumSet<UserMediaStatus> allowed = switch (mediaType) {
            case MOVIE, TRACK -> EnumSet.of(UserMediaStatus.PLANNED, UserMediaStatus.COMPLETED);
            case ALBUM -> EnumSet.of(
                    UserMediaStatus.PLANNED,
                    UserMediaStatus.IN_PROGRESS,
                    UserMediaStatus.COMPLETED
            );
            case BOOK, SERIES -> EnumSet.allOf(UserMediaStatus.class);
            case EPISODE -> EnumSet.noneOf(UserMediaStatus.class);
        };

        if (!allowed.contains(status)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_MEDIA_STATUS",
                    "Este estado não está disponível para o tipo de mídia"
            );
        }
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
}
