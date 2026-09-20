package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.media.dto.response.AnticipatedMediaItemResponse;
import com.scriptles.cabinet.media.dto.response.AnticipatedMediaResponse;
import com.scriptles.cabinet.media.dto.response.MediaSearchItemResponse;
import com.scriptles.cabinet.media.dto.response.TrendingMediaResponse;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.repository.MediaLikeRepository;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.media.repository.RatingRepository;
import com.scriptles.cabinet.user.enums.Visibility;
import com.scriptles.cabinet.user.enums.UserMediaStatus;
import com.scriptles.cabinet.user.repository.UserMediaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MediaRankingService {
    private static final Set<MediaType> DEFAULT_TYPES = EnumSet.of(
            MediaType.MOVIE,
            MediaType.SERIES,
            MediaType.ALBUM,
            MediaType.BOOK
    );
    private static final int RATING_ACTIVITY_WEIGHT = 3;
    private static final int LIKE_ACTIVITY_WEIGHT = 2;
    private static final int LIBRARY_ACTIVITY_WEIGHT = 1;

    private final RatingRepository ratingRepository;
    private final MediaLikeRepository mediaLikeRepository;
    private final UserMediaRepository userMediaRepository;
    private final MediaRepository mediaRepository;
    private final MediaSearchItemAssembler mediaSearchItemAssembler;

    public PageResponse<MediaSearchItemResponse> topRated(
            MediaType type, int page, int limit, String locale) {
        Page<RatingRepository.RatedMediaProjection> ratings = ratingRepository.findTopRatedMedia(
                typeValues(type),
                Visibility.PUBLIC,
                PageRequest.of(page, limit)
        );
        List<Media> media = ratings.getContent().stream()
                .map(RatingRepository.RatedMediaProjection::getMedia)
                .toList();

        return new PageResponse<>(
                mediaSearchItemAssembler.fromImported(media, locale),
                ratings.getNumber(),
                ratings.getSize(),
                ratings.getTotalElements(),
                ratings.getTotalPages()
        );
    }

    public TrendingMediaResponse trending(MediaType type, int periodDays, int limit, String locale) {
        Set<String> types = typeValues(type);
        Instant since = Instant.now(Clock.systemUTC()).minus(periodDays, ChronoUnit.DAYS);
        int candidateLimit = Math.min(limit * 5, 200);
        PageRequest candidates = PageRequest.of(0, candidateLimit);
        Map<UUID, Long> scores = new HashMap<>();

        addScores(
                scores,
                ratingRepository.findRecentActivity(types, Visibility.PUBLIC, since, candidates),
                RatingRepository.MediaActivityProjection::getMediaId,
                RatingRepository.MediaActivityProjection::getActivityCount,
                RATING_ACTIVITY_WEIGHT
        );
        addScores(
                scores,
                mediaLikeRepository.findRecentActivity(types, since, candidates),
                MediaLikeRepository.MediaActivityProjection::getMediaId,
                MediaLikeRepository.MediaActivityProjection::getActivityCount,
                LIKE_ACTIVITY_WEIGHT
        );
        addScores(
                scores,
                userMediaRepository.findRecentPublicActivity(types, since, candidates),
                UserMediaRepository.MediaActivityProjection::getMediaId,
                UserMediaRepository.MediaActivityProjection::getActivityCount,
                LIBRARY_ACTIVITY_WEIGHT
        );

        if (scores.isEmpty()) {
            return new TrendingMediaResponse(List.of(), periodDays);
        }

        Map<UUID, Media> mediaById = mediaRepository.findAllById(scores.keySet()).stream()
                .collect(Collectors.toMap(Media::getId, media -> media));
        List<MediaSearchItemResponse> assembled = mediaSearchItemAssembler.fromImported(
                scores.keySet().stream().map(mediaById::get).filter(java.util.Objects::nonNull).toList(),
                locale
        );
        Map<UUID, MediaSearchItemResponse> itemById = assembled.stream()
                .collect(Collectors.toMap(MediaSearchItemResponse::id, item -> item));

        Comparator<MediaSearchItemResponse> comparator = Comparator
                .comparingLong((MediaSearchItemResponse item) -> scores.getOrDefault(item.id(), 0L)).reversed()
                .thenComparing(MediaSearchItemResponse::averageRating,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Comparator.comparingLong(MediaSearchItemResponse::ratingCount).reversed())
                .thenComparing(MediaSearchItemResponse::title, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(MediaSearchItemResponse::id);

        List<MediaSearchItemResponse> items = itemById.values().stream()
                .sorted(comparator)
                .limit(limit)
                .toList();
        return new TrendingMediaResponse(items, periodDays);
    }

    public AnticipatedMediaResponse anticipated(int limit, String locale) {
        List<UserMediaRepository.AnticipatedMediaProjection> ranking =
                userMediaRepository.findMostAnticipatedMovies(
                        UserMediaStatus.PLANNED,
                        LocalDate.now(),
                        PageRequest.of(0, limit)
                );
        if (ranking.isEmpty()) {
            return new AnticipatedMediaResponse(List.of());
        }

        Map<UUID, Long> plannedCounts = ranking.stream().collect(Collectors.toMap(
                projection -> projection.getMedia().getId(),
                UserMediaRepository.AnticipatedMediaProjection::getPlannedCount
        ));
        List<MediaSearchItemResponse> assembled = mediaSearchItemAssembler.fromImported(
                ranking.stream().map(UserMediaRepository.AnticipatedMediaProjection::getMedia).toList(),
                locale
        );
        List<AnticipatedMediaItemResponse> items = assembled.stream()
                .map(item -> AnticipatedMediaItemResponse.from(
                        item,
                        plannedCounts.getOrDefault(item.id(), 0L)
                ))
                .toList();
        return new AnticipatedMediaResponse(items);
    }

    private Set<String> typeValues(MediaType type) {
        Set<MediaType> types = type == null ? DEFAULT_TYPES : EnumSet.of(type);
        return types.stream().map(Enum::name).collect(Collectors.toUnmodifiableSet());
    }

    private <T> void addScores(
            Map<UUID, Long> scores,
            List<T> activities,
            java.util.function.Function<T, UUID> mediaId,
            ToLongFunction<T> activityCount,
            int weight
    ) {
        activities.forEach(activity -> scores.merge(
                mediaId.apply(activity),
                activityCount.applyAsLong(activity) * weight,
                Long::sum
        ));
    }
}
