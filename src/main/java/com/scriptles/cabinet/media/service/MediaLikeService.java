package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.media.dto.response.MediaLikeResponse;
import com.scriptles.cabinet.media.repository.MediaLikeRepository;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MediaLikeService {
    private final MediaLikeRepository mediaLikeRepository;
    private final UserRepository userRepository;
    private final MediaRepository mediaRepository;

    @Transactional(readOnly = true)
    public MediaLikeResponse find(UUID userId, UUID mediaId) {
        return new MediaLikeResponse(mediaLikeRepository.existsByUserIdAndMediaId(userId, mediaId));
    }

    @Transactional
    public MediaLikeResponse like(UUID userId, UUID mediaId) {
        if (mediaLikeRepository.existsByUserIdAndMediaId(userId, mediaId)) {
            return new MediaLikeResponse(true);
        }
        findUser(userId);
        findMedia(mediaId);
        mediaLikeRepository.insertIfAbsent(UUID.randomUUID(), userId, mediaId);
        return new MediaLikeResponse(true);
    }

    @Transactional
    public void unlike(UUID userId, UUID mediaId) {
        mediaLikeRepository.deleteByUserIdAndMediaId(userId, mediaId);
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
