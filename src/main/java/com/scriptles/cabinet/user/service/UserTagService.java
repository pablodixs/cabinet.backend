package com.scriptles.cabinet.user.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.user.dto.response.ProfileTagResponse;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.entity.UserMediaTag;
import com.scriptles.cabinet.user.entity.UserTag;
import com.scriptles.cabinet.user.repository.UserMediaTagRepository;
import com.scriptles.cabinet.user.repository.UserRepository;
import com.scriptles.cabinet.user.repository.UserTagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserTagService {
    private static final int MAX_TAGS_PER_USER = 200;

    private final UserTagRepository tagRepository;
    private final UserMediaTagRepository mediaTagRepository;
    private final UserRepository userRepository;
    private final MediaRepository mediaRepository;

    @Transactional(readOnly = true)
    public List<ProfileTagResponse> findMine(UUID userId) {
        Map<UUID, Long> usageByTag = mediaTagRepository.countUsageByUserId(userId)
                .stream()
                .collect(Collectors.toMap(
                        UserMediaTagRepository.TagUsageCount::getTagId,
                        UserMediaTagRepository.TagUsageCount::getUsageCount));
        return tagRepository.findAllByUserIdOrderByNameAsc(userId).stream()
                .map(tag -> new ProfileTagResponse(
                        tag.getName(), usageByTag.getOrDefault(tag.getId(), 0L)))
                .toList();
    }

    @Transactional
    public ProfileTagResponse create(UUID userId, String requestedName) {
        String name = displayName(requestedName);
        String normalizedName = normalizedName(name);
        if (tagRepository.findByUserIdAndNormalizedName(userId, normalizedName).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "TAG_ALREADY_EXISTS",
                    "Você já criou essa tag");
        }
        User user = findUser(userId);
        ensureCapacity(userId, 1);
        UserTag tag = new UserTag();
        tag.setUser(user);
        tag.setName(name);
        tag.setNormalizedName(normalizedName);
        tagRepository.saveAndFlush(tag);
        return new ProfileTagResponse(tag.getName(), 0);
    }

    @Transactional(readOnly = true)
    public List<String> findMediaTags(UUID userId, UUID mediaId) {
        return mediaTagRepository
                .findAllByTagUserIdAndMediaIdOrderByTagNameAsc(userId, mediaId)
                .stream()
                .map(mediaTag -> mediaTag.getTag().getName())
                .toList();
    }

    @Transactional
    public List<String> replaceMediaTags(
            UUID userId, UUID mediaId, Collection<String> requestedTags) {
        User user = findUser(userId);
        Media media = mediaRepository.findById(mediaId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND",
                        "Mídia não encontrada"));
        Map<String, String> names = normalize(requestedTags);
        Map<String, UserTag> tagsByName = findOrCreate(user, names);

        mediaTagRepository.deleteAllByUserIdAndMediaId(userId, mediaId);
        mediaTagRepository.flush();
        List<UserMediaTag> mediaTags = names.keySet().stream()
                .map(normalizedName -> mediaTag(tagsByName.get(normalizedName), media))
                .toList();
        mediaTagRepository.saveAll(mediaTags);
        return mediaTags.stream().map(item -> item.getTag().getName()).toList();
    }

    @Transactional
    public void ensureTags(User user, Collection<String> requestedTags) {
        findOrCreate(user, normalize(requestedTags));
    }

    @Transactional
    public List<UserTag> resolveTags(
            User user, Collection<String> requestedTags) {
        Map<String, String> names = normalize(requestedTags);
        Map<String, UserTag> tagsByName = findOrCreate(user, names);
        return names.keySet().stream().map(tagsByName::get).toList();
    }

    private Map<String, UserTag> findOrCreate(User user, Map<String, String> names) {
        if (names.isEmpty()) return Map.of();
        Map<String, UserTag> existing = tagRepository
                .findAllByUserIdAndNormalizedNameIn(user.getId(), names.keySet())
                .stream()
                .collect(Collectors.toMap(UserTag::getNormalizedName, tag -> tag));
        int missing = names.size() - existing.size();
        ensureCapacity(user.getId(), missing);

        names.forEach((normalizedName, name) -> {
            if (existing.containsKey(normalizedName)) return;
            UserTag tag = new UserTag();
            tag.setUser(user);
            tag.setName(name);
            tag.setNormalizedName(normalizedName);
            existing.put(normalizedName, tagRepository.save(tag));
        });
        return existing;
    }

    private UserMediaTag mediaTag(UserTag tag, Media media) {
        UserMediaTag mediaTag = new UserMediaTag();
        mediaTag.setTag(tag);
        mediaTag.setMedia(media);
        return mediaTag;
    }

    private Map<String, String> normalize(Collection<String> requestedTags) {
        if (requestedTags == null || requestedTags.isEmpty()) return Map.of();
        Map<String, String> names = new LinkedHashMap<>();
        requestedTags.forEach(requestedName -> {
            String name = displayName(requestedName);
            names.putIfAbsent(normalizedName(name), name);
        });
        return names;
    }

    private String displayName(String requestedName) {
        String name = requestedName == null ? "" : requestedName.trim();
        while (name.startsWith("#")) name = name.substring(1).trim();
        if (name.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TAG",
                    "Informe um nome para a tag");
        }
        if (name.length() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TAG",
                    "A tag deve ter no máximo 100 caracteres");
        }
        return name;
    }

    private String normalizedName(String name) {
        return Normalizer.normalize(name, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
    }

    private void ensureCapacity(UUID userId, int newTags) {
        if (newTags > 0 && tagRepository.countByUserId(userId) + newTags > MAX_TAGS_PER_USER) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TAG_LIMIT_EXCEEDED",
                    "Você pode criar no máximo 200 tags");
        }
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND",
                        "Usuário não encontrado"));
    }
}
