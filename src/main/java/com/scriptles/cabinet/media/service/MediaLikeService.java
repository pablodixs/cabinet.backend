package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.common.outbox.DomainEventType;
import com.scriptles.cabinet.common.outbox.DomainOutboxPublisher;
import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.media.dto.response.MediaLikeResponse;
import com.scriptles.cabinet.media.repository.MediaLikeRepository;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.user.repository.UserRepository;
import com.scriptles.cabinet.user.service.UserFeedService;
import com.scriptles.cabinet.user.service.InterestProfileCache;
import com.scriptles.cabinet.user.enums.FeedActionType;
import com.scriptles.cabinet.user.enums.Visibility;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MediaLikeService {
    private final MediaLikeRepository mediaLikeRepository;
    private final UserRepository userRepository;
    private final MediaRepository mediaRepository;
    private final UserFeedService userFeedService;
    private final InterestProfileCache interestProfileCache;
    private final DomainOutboxPublisher domainOutboxPublisher;

    @Transactional(readOnly = true)
    public MediaLikeResponse find(UUID userId, UUID mediaId) {
        return new MediaLikeResponse(mediaLikeRepository.existsByUserIdAndMediaId(userId, mediaId));
    }

    @Transactional
    @CacheEvict(cacheNames = "mediaCommunity", key = "#mediaId")
    public MediaLikeResponse like(UUID userId, UUID mediaId) {
        interestProfileCache.invalidate(userId);
        if (mediaLikeRepository.existsByUserIdAndMediaId(userId, mediaId)) {
            return new MediaLikeResponse(true);
        }
        var user = userRepository.findById(userId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Usuário não encontrado"));
        var media = mediaRepository.findById(mediaId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Mídia não encontrada"));
        if (media.getType() == MediaType.EPISODE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_MEDIA_CAPABILITY",
                    "Episódios não podem ser curtidos diretamente");
        }
        int inserted = mediaLikeRepository.insertIfAbsent(UUID.randomUUID(), userId, mediaId);
        if (inserted == 0) return new MediaLikeResponse(true);
        domainOutboxPublisher.publishMediaEvent(DomainEventType.MEDIA_LIKED, mediaId,
                java.util.Map.of("userId", userId.toString()));
        userFeedService.record(user, media, FeedActionType.LIKED, java.time.Instant.now(), Visibility.PUBLIC,
                null, null, false);
        return new MediaLikeResponse(true);
    }

    @Transactional
    @CacheEvict(cacheNames = "mediaCommunity", key = "#mediaId")
    public void unlike(UUID userId, UUID mediaId) {
        interestProfileCache.invalidate(userId);
        long deleted = mediaLikeRepository.deleteByUserIdAndMediaId(userId, mediaId);
        if (deleted > 0) {
            domainOutboxPublisher.publishMediaEvent(DomainEventType.MEDIA_UNLIKED, mediaId,
                    java.util.Map.of("userId", userId.toString()));
        }
        userFeedService.remove(userId, mediaId, FeedActionType.LIKED);
    }

    private void findUser(UUID userId) {
        if (userRepository.findById(userId).isEmpty()) {
            throw new ApiException(
                HttpStatus.NOT_FOUND,
                "USER_NOT_FOUND",
                "Usuário não encontrado"
            );
        }
    }

    private void findMedia(UUID mediaId) {
        var media = mediaRepository.findById(mediaId).orElse(null);
        if (media == null) {
            throw new ApiException(
                HttpStatus.NOT_FOUND,
                "MEDIA_NOT_FOUND",
                "Mídia não encontrada"
            );
        }
        if (media.getType() == MediaType.EPISODE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_MEDIA_CAPABILITY",
                    "Episódios não podem ser curtidos diretamente");
        }
    }
}
