package com.scriptles.cabinet.user.service;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.repository.MediaRepository;
import com.scriptles.cabinet.user.entity.User;
import com.scriptles.cabinet.user.entity.UserProfileFavorite;
import com.scriptles.cabinet.user.repository.UserProfileFavoriteRepository;
import com.scriptles.cabinet.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProfileFavoriteService {
    private final UserProfileFavoriteRepository favoriteRepository;
    private final UserRepository userRepository;
    private final MediaRepository mediaRepository;

    @Transactional
    public void replace(UUID userId, List<UUID> mediaIds) {
        LinkedHashSet<UUID> uniqueIds = new LinkedHashSet<>(mediaIds);
        if (uniqueIds.size() != mediaIds.size()) {
            throw badRequest("duplicate_favorites",
                    "Uma obra não pode aparecer mais de uma vez nos favoritos");
        }
        if (uniqueIds.size() > 4) {
            throw badRequest("favorite_limit_exceeded",
                    "Você pode selecionar no máximo quatro obras favoritas");
        }

        Map<UUID, Media> mediaById = mediaRepository.findAllById(uniqueIds).stream()
                .collect(Collectors.toMap(Media::getId, Function.identity()));
        if (mediaById.size() != uniqueIds.size()) {
            throw badRequest("favorite_not_found",
                    "Uma ou mais obras favoritas não foram encontradas");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> badRequest("user_not_found", "Usuário não encontrado"));
        favoriteRepository.deleteAllByUserId(userId);
        favoriteRepository.flush();

        List<UserProfileFavorite> favorites = mediaIds.stream()
                .map(mediaId -> favorite(user, mediaById.get(mediaId), mediaIds.indexOf(mediaId)))
                .toList();
        favoriteRepository.saveAll(favorites);
    }

    private UserProfileFavorite favorite(User user, Media media, int position) {
        UserProfileFavorite favorite = new UserProfileFavorite();
        favorite.setUser(user);
        favorite.setMedia(media);
        favorite.setPosition(position);
        return favorite;
    }

    private ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }
}
