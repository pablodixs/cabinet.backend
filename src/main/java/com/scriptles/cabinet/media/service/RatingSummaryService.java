package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.common.time.CabinetTime;
import com.scriptles.cabinet.media.entity.Rating;
import com.scriptles.cabinet.media.entity.SeriesEpisode;
import com.scriptles.cabinet.media.repository.RatingRepository;
import com.scriptles.cabinet.user.enums.Visibility;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RatingSummaryService {
    private final RatingRepository ratingRepository;

    public Map<UUID, ItemStats> items(Collection<UUID> mediaIds, UUID userId) {
        if (mediaIds.isEmpty()) return Map.of();
        Map<UUID, ItemStats> result = new HashMap<>();
        ratingRepository.summarizeRatings(mediaIds, Visibility.PUBLIC).forEach(summary ->
                result.put(summary.getMediaId(), new ItemStats(
                        rounded(summary.getAverageRating()), summary.getRatingCount(), null)));
        if (userId != null) {
            ratingRepository.findAllByUserIdAndMediaIdIn(userId, mediaIds).forEach(rating -> {
                ItemStats current = result.getOrDefault(rating.getMedia().getId(), ItemStats.empty());
                result.put(rating.getMedia().getId(), new ItemStats(
                        current.averageRating(), current.ratingCount(), rating.getValue().doubleValue()));
            });
        }
        return Map.copyOf(result);
    }

    public SeasonStats season(List<SeriesEpisode> episodes, UUID userId) {
        List<SeriesEpisode> eligible = episodes.stream().filter(this::eligible).toList();
        List<UUID> ids = eligible.stream().map(e -> e.getEpisodeMedia().getId()).toList();
        if (ids.isEmpty()) return SeasonStats.empty();

        List<Rating> publicRatings = ratingRepository.findAllByMediaIdInAndVisibility(ids, Visibility.PUBLIC);
        Map<UUID, List<Rating>> byUser = publicRatings.stream()
                .collect(Collectors.groupingBy(r -> r.getUser().getId()));
        Double community = byUser.isEmpty() ? null : rounded(byUser.values().stream()
                .mapToDouble(this::average).average().orElseThrow());

        List<Rating> mine = userId == null ? List.of()
                : ratingRepository.findAllByUserIdAndMediaIdIn(userId, ids);
        Double myRating = mine.isEmpty() ? null : rounded(average(mine));
        return new SeasonStats(community, byUser.size(), myRating, mine.size(), eligible.size());
    }

    public boolean eligible(SeriesEpisode episode) {
        return episode.getAirDate() == null || !episode.getAirDate().isAfter(CabinetTime.today());
    }

    private double average(List<Rating> ratings) {
        return ratings.stream().map(Rating::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(ratings.size()), 8, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private static Double rounded(Double value) {
        return value == null ? null : BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    public record ItemStats(Double averageRating, long ratingCount, Double myRating) {
        public static ItemStats empty() { return new ItemStats(null, 0, null); }
    }

    public record SeasonStats(Double averageRating, long ratingCount, Double myRating,
                              long myRatedEpisodeCount, long eligibleEpisodeCount) {
        public static SeasonStats empty() { return new SeasonStats(null, 0, null, 0, 0); }
    }
}
