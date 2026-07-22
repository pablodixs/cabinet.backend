package com.scriptles.cabinet.user.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.MediaCredit;
import com.scriptles.cabinet.media.entity.Person;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.repository.MediaCreditRepository;
import com.scriptles.cabinet.media.repository.MediaLikeRepository;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.media.repository.PersonRepository;
import com.scriptles.cabinet.media.repository.RatingRepository;
import com.scriptles.cabinet.user.dto.request.UpsertInterestPreferenceRequest;
import com.scriptles.cabinet.user.dto.response.InterestOptionResponse;
import com.scriptles.cabinet.user.dto.response.InterestResponse;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.entity.UserInterestPreference;
import com.scriptles.cabinet.user.enums.InterestPreference;
import com.scriptles.cabinet.user.enums.InterestTargetType;
import com.scriptles.cabinet.user.repository.UserInterestPreferenceRepository;
import com.scriptles.cabinet.user.repository.UserMediaRepository;
import com.scriptles.cabinet.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InterestGraphService {
    private static final Set<MediaType> SUPPORTED_MEDIA_TYPES = EnumSet.of(
            MediaType.MOVIE, MediaType.SERIES, MediaType.ALBUM, MediaType.BOOK);
    private static final double EPSILON = 0.00001;

    private final InterestScoringPolicy policy;
    private final UserInterestPreferenceRepository preferenceRepository;
    private final RatingRepository ratingRepository;
    private final MediaLikeRepository mediaLikeRepository;
    private final UserMediaRepository userMediaRepository;
    private final UserRepository userRepository;
    private final MediaRepository mediaRepository;
    private final MediaCreditRepository mediaCreditRepository;
    private final PersonRepository personRepository;

    @Transactional(readOnly = true)
    public PageResponse<InterestResponse> interests(
            UUID userId, InterestTargetType targetType, int page, int size) {
        List<InterestResponse> all = build(userId).nodes().values().stream()
                .filter(node -> node.key().type() == targetType)
                .filter(node -> Math.abs(node.effectiveScore()) > EPSILON)
                .sorted(Comparator.comparingDouble((InterestNode node) -> Math.abs(node.effectiveScore())).reversed()
                        .thenComparing(InterestNode::label, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(node -> node.key().targetId()))
                .map(this::response)
                .toList();
        int from = (int) Math.min((long) page * size, all.size());
        int to = Math.min(from + size, all.size());
        int pages = all.isEmpty() ? 0 : (int) Math.ceil((double) all.size() / size);
        return new PageResponse<>(all.subList(from, to), page, size, all.size(), pages);
    }

    @Transactional
    public InterestResponse upsert(UUID userId, UpsertInterestPreferenceRequest request) {
        User user = userRepository.findById(userId).orElseThrow(() -> notFound(
                "USER_NOT_FOUND", "Usuário não encontrado"));
        ValidTarget target = validateTarget(request.targetType(), request.targetId());
        UserInterestPreference entity = findPreference(userId, target).orElseGet(() -> {
            UserInterestPreference created = new UserInterestPreference();
            created.setUser(user);
            created.setTargetType(target.type());
            return created;
        });
        entity.setPreference(request.preference());
        entity.setGenreKey(target.genreKey());
        entity.setGenreLabel(target.genreLabel());
        entity.setPerson(target.person());
        entity.setMedia(target.media());
        preferenceRepository.saveAndFlush(entity);
        return response(build(userId).nodes().get(target.key()));
    }

    @Transactional
    public void delete(UUID userId, InterestTargetType targetType, String targetId) {
        ValidTarget target = parseTarget(targetType, targetId);
        findPreference(userId, target).ifPresent(preferenceRepository::delete);
        preferenceRepository.flush();
    }

    @Transactional(readOnly = true)
    public List<InterestOptionResponse> options(
            InterestTargetType targetType, String query, MediaType mediaType, int limit) {
        if (mediaType != null) requireSupported(mediaType);
        String normalizedQuery = query == null ? "" : query.trim();
        PageRequest page = PageRequest.of(0, limit);
        return switch (targetType) {
            case GENRE -> mediaRepository.findGenreOptions(
                            mediaType == null ? null : mediaType.name(), normalizedQuery, page).stream()
                    .collect(Collectors.toMap(policy::normalizeGenre, genre -> genre,
                            (first, ignored) -> first, LinkedHashMap::new))
                    .entrySet().stream()
                    .map(entry -> new InterestOptionResponse(
                            targetType, entry.getKey(), entry.getValue(), null, null))
                    .toList();
            case PERSON -> personRepository
                    .findInterestOptions(normalizedQuery,
                            mediaType == null ? null : mediaType.name(), page).stream()
                    .map(person -> new InterestOptionResponse(targetType, person.getId().toString(),
                            person.getName(), null, person.getImageUrl()))
                    .toList();
            case MEDIA -> mediaRepository.findInterestMediaOptions(
                            mediaTypeValues(mediaType), normalizedQuery, page).stream()
                    .map(media -> new InterestOptionResponse(targetType, media.getId().toString(),
                            media.getTitle(), media.getType().name(), media.getCoverUrl()))
                    .toList();
        };
    }

    @Transactional(readOnly = true)
    public InterestProfile build(UUID userId) {
        Map<UUID, Double> rawSeeds = new LinkedHashMap<>();
        Set<UUID> interacted = new LinkedHashSet<>();

        ratingRepository.findAllByUserId(userId).forEach(rating -> {
            UUID mediaId = rating.getMedia().getId();
            rawSeeds.merge(mediaId, policy.rating(rating.getValue()), Double::sum);
            interacted.add(mediaId);
        });
        mediaLikeRepository.findAllByUserId(userId).forEach(like -> {
            UUID mediaId = like.getMedia().getId();
            rawSeeds.merge(mediaId, 2.0, Double::sum);
            interacted.add(mediaId);
        });
        userMediaRepository.findAllByUserId(userId).forEach(entry -> {
            UUID mediaId = entry.getMedia().getId();
            rawSeeds.merge(mediaId, policy.library(entry.getStatus()), Double::sum);
            interacted.add(mediaId);
        });

        List<UserInterestPreference> preferences = preferenceRepository.findAllByUserId(userId);
        Map<InterestKey, UserInterestPreference> explicit = preferences.stream()
                .collect(Collectors.toMap(this::key, preference -> preference));
        preferences.stream().filter(preference -> preference.getTargetType() == InterestTargetType.MEDIA)
                .forEach(preference -> interacted.add(preference.getMedia().getId()));

        Set<UUID> seedIds = new LinkedHashSet<>(rawSeeds.keySet());
        preferences.stream().filter(preference -> preference.getTargetType() == InterestTargetType.MEDIA)
                .map(preference -> preference.getMedia().getId()).forEach(seedIds::add);
        Map<UUID, Media> mediaById = seedIds.isEmpty() ? Map.of() : mediaRepository
                .findAllWithGenresByIdIn(seedIds).stream()
                .collect(Collectors.toMap(Media::getId, media -> media));
        Map<UUID, List<MediaCredit>> creditsByMedia = seedIds.isEmpty() ? Map.of() : mediaCreditRepository
                .findAllByMediaIdInOrderByPositionAsc(seedIds).stream()
                .collect(Collectors.groupingBy(credit -> credit.getMedia().getId(), LinkedHashMap::new,
                        Collectors.toList()));

        Map<InterestKey, MutableNode> inferred = new LinkedHashMap<>();
        rawSeeds.forEach((mediaId, raw) -> {
            Media media = mediaById.get(mediaId);
            if (media != null && SUPPORTED_MEDIA_TYPES.contains(media.getType())) {
                add(inferred, new InterestKey(InterestTargetType.MEDIA, mediaId.toString()),
                        media.getTitle(), policy.implicitSeed(raw));
            }
        });

        seedIds.forEach(mediaId -> {
            Media media = mediaById.get(mediaId);
            if (media == null || !SUPPORTED_MEDIA_TYPES.contains(media.getType())) return;
            InterestKey mediaKey = new InterestKey(InterestTargetType.MEDIA, mediaId.toString());
            UserInterestPreference mediaPreference = explicit.get(mediaKey);
            double seed = mediaPreference == null
                    ? policy.implicitSeed(rawSeeds.getOrDefault(mediaId, 0.0))
                    : policy.explicit(mediaPreference.getPreference());
            if (Math.abs(seed) <= EPSILON) return;

            List<GenreValue> genres = media.getGenres().stream()
                    .map(label -> new GenreValue(policy.normalizeGenre(label), label))
                    .collect(Collectors.toMap(GenreValue::key, value -> value,
                            (first, ignored) -> first, LinkedHashMap::new)).values().stream().toList();
            if (!genres.isEmpty()) {
                double contribution = seed * InterestScoringPolicy.GENRE_SHARE / genres.size();
                genres.forEach(genre -> add(inferred,
                        new InterestKey(InterestTargetType.GENRE, genre.key()), genre.label(), contribution));
            }

            List<MediaCredit> credits = creditsByMedia.getOrDefault(mediaId, List.of());
            double roleTotal = credits.stream()
                    .mapToDouble(credit -> policy.role(credit.getRole(), credit.getPosition())).sum();
            if (roleTotal > 0) {
                credits.forEach(credit -> add(inferred,
                        new InterestKey(InterestTargetType.PERSON, credit.getPerson().getId().toString()),
                        credit.getPerson().getName(), seed * InterestScoringPolicy.PERSON_SHARE
                                * policy.role(credit.getRole(), credit.getPosition()) / roleTotal));
            }
        });

        Map<InterestKey, InterestNode> nodes = new LinkedHashMap<>();
        inferred.forEach((interestKey, mutable) -> {
            double inferredScore = policy.inferredNode(mutable.score());
            UserInterestPreference preference = explicit.get(interestKey);
            nodes.put(interestKey, new InterestNode(interestKey, mutable.label(), inferredScore,
                    preference == null ? inferredScore : policy.explicit(preference.getPreference()),
                    preference == null ? null : preference.getPreference()));
        });
        preferences.forEach(preference -> {
            InterestKey interestKey = key(preference);
            nodes.computeIfAbsent(interestKey, ignored -> new InterestNode(
                    interestKey, label(preference), 0.0, policy.explicit(preference.getPreference()),
                    preference.getPreference()));
        });
        return new InterestProfile(Map.copyOf(nodes), Set.copyOf(interacted));
    }

    public Set<MediaType> supportedMediaTypes() {
        return Set.copyOf(SUPPORTED_MEDIA_TYPES);
    }

    private InterestResponse response(InterestNode node) {
        InterestPreference polarity = node.effectiveScore() >= 0
                ? InterestPreference.POSITIVE : InterestPreference.NEGATIVE;
        return new InterestResponse(node.key().type(), node.key().targetId(), node.label(), polarity,
                node.explicitPreference(), Math.abs(node.inferredScore()) > EPSILON,
                Math.round(policy.strength(node.effectiveScore()) * 1000.0) / 1000.0);
    }

    private ValidTarget validateTarget(InterestTargetType type, String targetId) {
        ValidTarget target = parseTarget(type, targetId);
        return switch (type) {
            case GENRE -> {
                List<String> labels = mediaRepository.findGenreLabels(target.genreKey(), PageRequest.of(0, 1));
                if (labels.isEmpty()) throw notFound("INTEREST_TARGET_NOT_FOUND", "Gênero não encontrado");
                yield new ValidTarget(type, target.genreKey(), labels.getFirst(), null, null);
            }
            case PERSON -> {
                Person person = personRepository.findById(target.person().getId()).orElseThrow(() ->
                        notFound("INTEREST_TARGET_NOT_FOUND", "Pessoa não encontrada"));
                yield new ValidTarget(type, null, null, person, null);
            }
            case MEDIA -> {
                Media media = mediaRepository.findById(target.media().getId()).orElseThrow(() ->
                        notFound("INTEREST_TARGET_NOT_FOUND", "Mídia não encontrada"));
                requireSupported(media.getType());
                yield new ValidTarget(type, null, null, null, media);
            }
        };
    }

    private ValidTarget parseTarget(InterestTargetType type, String targetId) {
        if (type == null || targetId == null || targetId.isBlank()) {
            throw invalidTarget();
        }
        if (type == InterestTargetType.GENRE) {
            String key = policy.normalizeGenre(targetId);
            if (key.length() > 100) throw invalidTarget();
            return new ValidTarget(type, key, null, null, null);
        }
        try {
            UUID id = UUID.fromString(targetId);
            if (type == InterestTargetType.PERSON) {
                Person reference = new Person();
                reference.setId(id);
                return new ValidTarget(type, null, null, reference, null);
            }
            Media reference = new Media();
            reference.setId(id);
            return new ValidTarget(type, null, null, null, reference);
        } catch (IllegalArgumentException exception) {
            throw invalidTarget();
        }
    }

    private java.util.Optional<UserInterestPreference> findPreference(UUID userId, ValidTarget target) {
        return switch (target.type()) {
            case GENRE -> preferenceRepository.findByUserIdAndTargetTypeAndGenreKey(
                    userId, target.type(), target.genreKey());
            case PERSON -> preferenceRepository.findByUserIdAndTargetTypeAndPersonId(
                    userId, target.type(), target.person().getId());
            case MEDIA -> preferenceRepository.findByUserIdAndTargetTypeAndMediaId(
                    userId, target.type(), target.media().getId());
        };
    }

    private InterestKey key(UserInterestPreference preference) {
        return switch (preference.getTargetType()) {
            case GENRE -> new InterestKey(InterestTargetType.GENRE, preference.getGenreKey());
            case PERSON -> new InterestKey(InterestTargetType.PERSON, preference.getPerson().getId().toString());
            case MEDIA -> new InterestKey(InterestTargetType.MEDIA, preference.getMedia().getId().toString());
        };
    }

    private String label(UserInterestPreference preference) {
        return switch (preference.getTargetType()) {
            case GENRE -> preference.getGenreLabel();
            case PERSON -> preference.getPerson().getName();
            case MEDIA -> preference.getMedia().getTitle();
        };
    }

    private void add(Map<InterestKey, MutableNode> nodes, InterestKey key, String label, double score) {
        nodes.compute(key, (ignored, current) -> current == null
                ? new MutableNode(label, score)
                : new MutableNode(current.label(), current.score() + score));
    }

    private Set<String> mediaTypeValues(MediaType mediaType) {
        return (mediaType == null ? SUPPORTED_MEDIA_TYPES : Set.of(mediaType)).stream()
                .map(Enum::name).collect(Collectors.toUnmodifiableSet());
    }

    private void requireSupported(MediaType type) {
        if (!SUPPORTED_MEDIA_TYPES.contains(type)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_RECOMMENDATION_TYPE",
                    "Este tipo de mídia não está disponível nas recomendações");
        }
    }

    private ApiException invalidTarget() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INTEREST_TARGET",
                "O alvo do interesse é inválido");
    }

    private ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }

    public record InterestKey(InterestTargetType type, String targetId) {
    }

    public record InterestNode(
            InterestKey key,
            String label,
            double inferredScore,
            double effectiveScore,
            InterestPreference explicitPreference
    ) {
    }

    public record InterestProfile(Map<InterestKey, InterestNode> nodes, Set<UUID> interactedMediaIds) {
    }

    private record MutableNode(String label, double score) {
    }

    private record GenreValue(String key, String label) {
    }

    private record ValidTarget(
            InterestTargetType type,
            String genreKey,
            String genreLabel,
            Person person,
            Media media
    ) {
        InterestKey key() {
            return switch (type) {
                case GENRE -> new InterestKey(type, genreKey);
                case PERSON -> new InterestKey(type, person.getId().toString());
                case MEDIA -> new InterestKey(type, media.getId().toString());
            };
        }
    }
}
