package com.scriptles.cabinet.media.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.media.dto.response.MediaLikeResponse;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.entity.MediaLike;
import com.scriptles.cabinet.media.repository.MediaLikeRepository;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.user.entity.User;
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

        MediaLike like = new MediaLike();
        like.setUser(findUser(userId));
        like.setMedia(findMedia(mediaId));
        mediaLikeRepository.saveAndFlush(like);
        return new MediaLikeResponse(true);
    }

    @Transactional
    public void unlike(UUID userId, UUID mediaId) {
        mediaLikeRepository.deleteByUserIdAndMediaId(userId, mediaId);
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
