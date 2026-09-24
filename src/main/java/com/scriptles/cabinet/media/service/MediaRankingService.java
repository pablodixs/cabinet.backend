package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.media.dto.response.AnticipatedMediaItemResponse;
import com.scriptles.cabinet.media.dto.response.AnticipatedMediaResponse;
import com.scriptles.cabinet.media.dto.response.MediaSearchItemResponse;
import com.scriptles.cabinet.media.dto.response.TrendingMediaResponse;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.repository.MediaRankingSnapshotRepository;
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

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
    private final RatingRepository ratingRepository;
    private final UserMediaRepository userMediaRepository;
    private final MediaRepository mediaRepository;
    private final MediaSearchItemAssembler mediaSearchItemAssembler;
    private final MediaRankingSnapshotRepository rankingSnapshotRepository;

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
        List<UUID> rankedIds = rankingSnapshotRepository.findTrendingMediaIds(types, periodDays, limit);
        if (rankedIds.isEmpty()) return new TrendingMediaResponse(List.of(), periodDays);

        Map<UUID, Media> mediaById = mediaRepository.findAllById(rankedIds).stream()
                .collect(Collectors.toMap(Media::getId, media -> media));
        List<Media> rankedMedia = rankedIds.stream()
                .map(mediaById::get)
                .filter(java.util.Objects::nonNull)
                .toList();
        List<MediaSearchItemResponse> items = mediaSearchItemAssembler.fromImported(rankedMedia, locale);
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

}
