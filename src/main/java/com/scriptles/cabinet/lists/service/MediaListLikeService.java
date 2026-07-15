package com.scriptles.cabinet.lists.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.lists.dto.response.MediaListLikeResponse;
import com.scriptles.cabinet.lists.entity.MediaList;
import com.scriptles.cabinet.lists.entity.MediaListLike;
import com.scriptles.cabinet.lists.repository.MediaListLikeRepository;
import com.scriptles.cabinet.lists.repository.MediaListRepository;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.enums.Visibility;
import com.scriptles.cabinet.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MediaListLikeService {
    private final MediaListLikeRepository mediaListLikeRepository;
    private final MediaListRepository mediaListRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public MediaListLikeResponse find(UUID userId, UUID listId) {
        findPublicList(listId);
        return response(userId, listId);
    }

    @Transactional
    public MediaListLikeResponse like(UUID userId, UUID listId) {
        MediaList list = findPublicList(listId);
        if (!mediaListLikeRepository.existsByUserIdAndListId(userId, listId)) {
            MediaListLike like = new MediaListLike();
            like.setUser(findUser(userId));
            like.setList(list);
            mediaListLikeRepository.saveAndFlush(like);
        }

        return response(userId, listId);
    }

    @Transactional
    public MediaListLikeResponse unlike(UUID userId, UUID listId) {
        findPublicList(listId);
        mediaListLikeRepository.deleteByUserIdAndListId(userId, listId);
        return response(userId, listId);
    }

    private MediaListLikeResponse response(UUID userId, UUID listId) {
        return new MediaListLikeResponse(
                mediaListLikeRepository.existsByUserIdAndListId(userId, listId),
                mediaListLikeRepository.countByListId(listId)
        );
    }

    private MediaList findPublicList(UUID listId) {
        return mediaListRepository.findById(listId)
                .filter(list -> list.getVisibility() == Visibility.PUBLIC)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "LIST_NOT_FOUND",
                        "Lista não encontrada"
                ));
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "USER_NOT_FOUND",
                "Usuário não encontrado"
        ));
    }
}
