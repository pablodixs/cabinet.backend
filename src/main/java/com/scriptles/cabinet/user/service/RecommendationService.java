package com.scriptles.cabinet.user.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.media.dto.response.MediaSearchItemResponse;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.MediaRelation;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.repository.MediaCreditRepository;
import com.scriptles.cabinet.media.repository.CreditScoringProjection;
import io.micrometer.core.instrument.MeterRegistry;
import com.scriptles.cabinet.media.repository.MediaRelationRepository;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.media.repository.RatingRepository;
import com.scriptles.cabinet.media.service.MediaRankingService;
import com.scriptles.cabinet.media.service.MediaSearchItemAssembler;
import com.scriptles.cabinet.user.dto.response.RecommendationItemResponse;
import com.scriptles.cabinet.user.dto.response.RecommendationReasonResponse;
import com.scriptles.cabinet.user.dto.response.RecommendationResponse;
import com.scriptles.cabinet.user.enums.InterestTargetType;
import com.scriptles.cabinet.user.enums.RecommendationReasonType;
import com.scriptles.cabinet.user.enums.RecommendationSource;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendationService {
    private static final int CREDIT_QUERY_BATCH_SIZE = 100;
    private static final UUID EMPTY_UUID = new UUID(0L, 0L);
    private static final int MAX_SEEDS_PER_TYPE = 50;
    private static final int MAX_TRENDING_CANDIDATES = 200;
    private static final String EMPTY_GENRE = "__no_genre__";

    private final InterestGraphService interestGraphService;
    private final InterestScoringPolicy policy;
    private final MediaRepository mediaRepository;
    private final MediaCreditRepository mediaCreditRepository;
    private final MediaRelationRepository mediaRelationRepository;
    private final RatingRepository ratingRepository;
    private final MediaSearchItemAssembler mediaSearchItemAssembler;
    private final MediaRankingService mediaRankingService;
    private MeterRegistry meterRegistry;

    @Autowired(required = false)
    void setMeterRegistry(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public RecommendationResponse recommendations(UUID userId, MediaType type, int limit, String locale) {
        validateType(type);
        InterestGraphService.InterestProfile profile = interestGraphService.build(userId);
        Set<String> types = typeValues(type);
        int poolSize = Math.max(200, limit * 20);

        List<InterestGraphService.InterestNode> positiveGenres = topPositive(
                profile, InterestTargetType.GENRE);
        List<InterestGraphService.InterestNode> positivePeople = topPositive(
                profile, InterestTargetType.PERSON);
        List<InterestGraphService.InterestNode> mediaNodes = topByAbsoluteScore(
                profile, InterestTargetType.MEDIA, MAX_SEEDS_PER_TYPE * 2);
        List<InterestGraphService.InterestNode> positiveMedia = topPositive(
                profile, InterestTargetType.MEDIA);

        Collection<String> genreKeys = positiveGenres.isEmpty()
                ? List.of(EMPTY_GENRE)
                : positiveGenres.stream().map(node -> node.key().targetId()).toList();
        Collection<UUID> personIds = positivePeople.isEmpty()
                ? List.of(EMPTY_UUID)
                : positivePeople.stream().map(node -> UUID.fromString(node.key().targetId())).toList();
        Collection<UUID> excluded = profile.interactedMediaIds().isEmpty()
                ? List.of(EMPTY_UUID) : profile.interactedMediaIds();

        LinkedHashSet<UUID> candidateIds = mediaRepository.findInterestCandidates(
                        types, excluded, genreKeys, personIds, PageRequest.of(0, poolSize)).stream()
                .map(Media::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        RelationContext relations = relationContext(mediaNodes, positiveMedia, types,
                profile.interactedMediaIds());
        candidateIds.addAll(relations.candidateIds());
        candidateIds.removeAll(profile.interactedMediaIds());
        if (candidateIds.isEmpty()) {
            return new RecommendationResponse(fallback(
                    userId, type, limit, profile.interactedMediaIds(), locale));
        }

        List<Media> candidates = loadMedia(candidateIds).stream()
                .filter(media -> types.contains(media.getType().name()))
                .toList();
        Set<UUID> relevantPeople = profile.nodes().keySet().stream()
                .filter(key -> key.type() == InterestTargetType.PERSON)
                .map(key -> UUID.fromString(key.targetId()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<CreditScoringProjection> scoringCredits = loadScoringCredits(candidateIds, relevantPeople);
        Map<UUID, List<CreditScoringProjection>> creditsByMedia = scoringCredits.stream()
                .collect(Collectors.groupingBy(CreditScoringProjection::getMediaId, LinkedHashMap::new,
                        Collectors.toList()));
        Map<UUID, RatingSummary> ratings = loadRatings(candidateIds);

        List<ScoredCandidate> scored = candidates.stream()
                .map(media -> score(media, creditsByMedia.getOrDefault(media.getId(), List.of()),
                        profile, relations.byCandidate().getOrDefault(media.getId(), List.of()),
                        ratings.getOrDefault(media.getId(), RatingSummary.EMPTY)))
                .filter(candidate -> candidate.score() > 0)
                .sorted(candidateComparator())
                .limit(limit)
                .toList();

        List<RecommendationItemResponse> personalized = assemblePersonalized(userId, scored, locale);
        if (personalized.size() == limit) {
            return new RecommendationResponse(personalized);
        }
        Set<UUID> fallbackExcluded = new LinkedHashSet<>(profile.interactedMediaIds());
        personalized.stream().map(item -> item.media().id()).forEach(fallbackExcluded::add);
        List<RecommendationItemResponse> items = new ArrayList<>(personalized);
        items.addAll(fallback(userId, type, limit - personalized.size(), fallbackExcluded, locale));
        return new RecommendationResponse(List.copyOf(items));
    }

    private List<CreditScoringProjection> loadScoringCredits(Set<UUID> mediaIds, Set<UUID> personIds) {
        if (meterRegistry != null) {
            meterRegistry.counter("cabinet.recommendations.candidates").increment(mediaIds.size());
        }
        if (mediaIds.isEmpty() || personIds.isEmpty()) return List.of();
        List<CreditScoringProjection> loaded = new ArrayList<>();
        List<UUID> media = List.copyOf(mediaIds);
        List<UUID> people = List.copyOf(personIds);
        for (int start = 0; start < media.size(); start += CREDIT_QUERY_BATCH_SIZE) {
            List<UUID> batch = media.subList(start, Math.min(start + CREDIT_QUERY_BATCH_SIZE, media.size()));
            for (int peopleStart = 0; peopleStart < people.size(); peopleStart += CREDIT_QUERY_BATCH_SIZE) {
                loaded.addAll(mediaCreditRepository.findScoringCredits(
                        batch, people.subList(peopleStart,
                                Math.min(peopleStart + CREDIT_QUERY_BATCH_SIZE, people.size()))));
            }
        }
        if (meterRegistry != null) {
            meterRegistry.counter("cabinet.recommendations.credits_loaded").increment(loaded.size());
        }
        return loaded;
    }

    private List<Media> loadMedia(Set<UUID> mediaIds) {
        List<UUID> ids = List.copyOf(mediaIds);
        List<Media> loaded = new ArrayList<>(ids.size());
        for (int start = 0; start < ids.size(); start += CREDIT_QUERY_BATCH_SIZE) {
            loaded.addAll(mediaRepository.findAllWithGenresByIdIn(
                    ids.subList(start, Math.min(start + CREDIT_QUERY_BATCH_SIZE, ids.size()))));
        }
        return loaded;
    }

    private Map<UUID, RatingSummary> loadRatings(Set<UUID> mediaIds) {
        List<UUID> ids = List.copyOf(mediaIds);
        Map<UUID, RatingSummary> loaded = new LinkedHashMap<>();
        for (int start = 0; start < ids.size(); start += CREDIT_QUERY_BATCH_SIZE) {
            ratingRepository.summarizeRatings(
                    ids.subList(start, Math.min(start + CREDIT_QUERY_BATCH_SIZE, ids.size())),
                    com.scriptles.cabinet.user.enums.Visibility.PUBLIC)
                    .forEach(projection -> loaded.put(projection.getMediaId(), new RatingSummary(
                            projection.getAverageRating(), projection.getRatingCount())));
        }
        return loaded;
    }

    private ScoredCandidate score(
            Media media,
            List<CreditScoringProjection> credits,
            InterestGraphService.InterestProfile profile,
            List<RelatedSeed> relations,
            RatingSummary rating
    ) {
        List<Contribution> contributions = new ArrayList<>();
        Map<String, String> genres = media.getGenres().stream()
                .collect(Collectors.toMap(policy::normalizeGenre, genre -> genre,
                        (first, ignored) -> first, LinkedHashMap::new));
        int genreCount = Math.max(1, genres.size());
        genres.forEach((key, label) -> {
            InterestGraphService.InterestNode node = profile.nodes().get(
                    new InterestGraphService.InterestKey(InterestTargetType.GENRE, key));
            if (node != null) {
                contributions.add(new Contribution(
                        new RecommendationReasonResponse(RecommendationReasonType.GENRE, key, label),
                        node.effectiveScore() / genreCount));
            }
        });

        double roleTotal = credits.stream()
                .mapToDouble(credit -> policy.role(credit.getRole(), credit.getPosition())).sum();
        if (roleTotal > 0) {
            credits.forEach(credit -> {
                String personId = credit.getPersonId().toString();
                InterestGraphService.InterestNode node = profile.nodes().get(
                        new InterestGraphService.InterestKey(InterestTargetType.PERSON, personId));
                if (node != null) {
                    double value = node.effectiveScore()
                            * policy.role(credit.getRole(), credit.getPosition()) / roleTotal;
                    contributions.add(new Contribution(new RecommendationReasonResponse(
                            RecommendationReasonType.PERSON, personId, credit.getPersonName()), value));
                }
            });
        }
        relations.forEach(related -> contributions.add(new Contribution(
                new RecommendationReasonResponse(RecommendationReasonType.MEDIA,
                        related.node().key().targetId(), related.node().label()),
                related.node().effectiveScore() * InterestScoringPolicy.RELATION_WEIGHT)));

        double score = contributions.stream().mapToDouble(Contribution::value).sum();
        List<RecommendationReasonResponse> reasons = contributions.stream()
                .filter(contribution -> contribution.value() > 0)
                .sorted(Comparator.comparingDouble(Contribution::value).reversed())
                .collect(Collectors.toMap(Contribution::reason, contribution -> contribution,
                        (first, ignored) -> first, LinkedHashMap::new))
                .values().stream().limit(3).map(Contribution::reason).toList();
        return new ScoredCandidate(media, score, rating.average(), rating.count(), reasons);
    }

    private RelationContext relationContext(
            List<InterestGraphService.InterestNode> mediaNodes,
            List<InterestGraphService.InterestNode> positiveMedia,
            Set<String> types,
            Set<UUID> excluded
    ) {
        if (mediaNodes.isEmpty()) return RelationContext.EMPTY;
        Map<UUID, InterestGraphService.InterestNode> nodesById = mediaNodes.stream()
                .collect(Collectors.toMap(node -> UUID.fromString(node.key().targetId()), node -> node));
        Set<UUID> positiveIds = positiveMedia.stream()
                .map(node -> UUID.fromString(node.key().targetId())).collect(Collectors.toSet());
        Map<UUID, List<RelatedSeed>> byCandidate = new HashMap<>();
        Set<UUID> candidateIds = new LinkedHashSet<>();
        for (MediaRelation relation : mediaRelationRepository.findAllConnectedTo(nodesById.keySet())) {
            UUID sourceId = relation.getSourceMedia().getId();
            UUID targetId = relation.getTargetMedia().getId();
            UUID seedId;
            Media candidate;
            if (nodesById.containsKey(sourceId)) {
                seedId = sourceId;
                candidate = relation.getTargetMedia();
            } else if (nodesById.containsKey(targetId)) {
                seedId = targetId;
                candidate = relation.getSourceMedia();
            } else {
                continue;
            }
            if (!types.contains(candidate.getType().name()) || excluded.contains(candidate.getId())) continue;
            byCandidate.computeIfAbsent(candidate.getId(), ignored -> new ArrayList<>())
                    .add(new RelatedSeed(nodesById.get(seedId)));
            if (positiveIds.contains(seedId)) candidateIds.add(candidate.getId());
        }
        return new RelationContext(Set.copyOf(candidateIds), Map.copyOf(byCandidate));
    }

    private List<RecommendationItemResponse> assemblePersonalized(
            UUID userId, List<ScoredCandidate> scored, String locale) {
        List<Media> orderedMedia = scored.stream().map(ScoredCandidate::media).toList();
        Map<UUID, MediaSearchItemResponse> assembled = mediaSearchItemAssembler
                .fromImported(orderedMedia, userId, locale).stream()
                .collect(Collectors.toMap(MediaSearchItemResponse::id, item -> item));
        return scored.stream().map(candidate -> new RecommendationItemResponse(
                        assembled.get(candidate.media().getId()), RecommendationSource.PERSONALIZED,
                        candidate.reasons()))
                .filter(item -> item.media() != null)
                .toList();
    }

    private List<RecommendationItemResponse> fallback(
            UUID userId, MediaType type, int limit, Set<UUID> excluded, String locale) {
        if (limit <= 0) return List.of();
        List<UUID> ids = mediaRankingService.trending(
                        type, 7, MAX_TRENDING_CANDIDATES, locale).items().stream()
                .map(MediaSearchItemResponse::id)
                .filter(Objects::nonNull)
                .filter(id -> !excluded.contains(id))
                .limit(limit)
                .toList();
        if (ids.isEmpty()) return List.of();
        Map<UUID, Media> mediaById = mediaRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Media::getId, media -> media));
        List<Media> ordered = ids.stream().map(mediaById::get).filter(Objects::nonNull).toList();
        return mediaSearchItemAssembler.fromImported(ordered, userId, locale).stream()
                .map(media -> new RecommendationItemResponse(media, RecommendationSource.TRENDING,
                        List.of(new RecommendationReasonResponse(
                                RecommendationReasonType.TRENDING, null, trendingReason(locale)))))
                .toList();
    }

    private String trendingReason(String locale) {
        return "en-US".equals(locale) ? "Trending on Cabinet" : "Em alta no Cabinet";
    }

    private List<InterestGraphService.InterestNode> topPositive(
            InterestGraphService.InterestProfile profile, InterestTargetType type) {
        return profile.nodes().values().stream()
                .filter(node -> node.key().type() == type && node.effectiveScore() > 0)
                .sorted(Comparator.comparingDouble(InterestGraphService.InterestNode::effectiveScore).reversed()
                        .thenComparing(node -> node.key().targetId()))
                .limit(MAX_SEEDS_PER_TYPE)
                .toList();
    }

    private List<InterestGraphService.InterestNode> topByAbsoluteScore(
            InterestGraphService.InterestProfile profile, InterestTargetType type, int limit) {
        return profile.nodes().values().stream()
                .filter(node -> node.key().type() == type && node.effectiveScore() != 0)
                .sorted(Comparator.comparingDouble(
                                (InterestGraphService.InterestNode node) -> Math.abs(node.effectiveScore())).reversed()
                        .thenComparing(node -> node.key().targetId()))
                .limit(limit)
                .toList();
    }

    private Comparator<ScoredCandidate> candidateComparator() {
        return Comparator.comparingDouble(ScoredCandidate::score).reversed()
                .thenComparing(ScoredCandidate::averageRating,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Comparator.comparingLong(ScoredCandidate::ratingCount).reversed())
                .thenComparing(candidate -> candidate.media().getTitle(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(candidate -> candidate.media().getId());
    }

    private Set<String> typeValues(MediaType type) {
        Set<MediaType> types = type == null
                ? EnumSet.copyOf(interestGraphService.supportedMediaTypes()) : EnumSet.of(type);
        return types.stream().map(Enum::name).collect(Collectors.toUnmodifiableSet());
    }

    private void validateType(MediaType type) {
        if (type != null && !interestGraphService.supportedMediaTypes().contains(type)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_RECOMMENDATION_TYPE",
                    "Este tipo de mídia não está disponível nas recomendações");
        }
    }

    private record Contribution(RecommendationReasonResponse reason, double value) {
    }

    private record RelatedSeed(InterestGraphService.InterestNode node) {
    }

    private record RelationContext(Set<UUID> candidateIds, Map<UUID, List<RelatedSeed>> byCandidate) {
        private static final RelationContext EMPTY = new RelationContext(Set.of(), Map.of());
    }

    private record RatingSummary(Double average, long count) {
        private static final RatingSummary EMPTY = new RatingSummary(null, 0);
    }

    private record ScoredCandidate(
            Media media,
            double score,
            Double averageRating,
            long ratingCount,
            List<RecommendationReasonResponse> reasons
    ) {
    }
}
