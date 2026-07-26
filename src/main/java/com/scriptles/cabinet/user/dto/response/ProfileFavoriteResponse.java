package com.scriptles.cabinet.user.dto.response;

import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.user.entity.UserProfileFavorite;

import java.util.UUID;

public record ProfileFavoriteResponse(
        UUID mediaId,
        MediaType type,
        String title,
        String coverUrl,
        int position
) {
    public static ProfileFavoriteResponse from(
            UserProfileFavorite favorite,
            String coverUrl
    ) {
        return new ProfileFavoriteResponse(
                favorite.getMedia().getId(),
                favorite.getMedia().getType(),
                favorite.getMedia().getTitle(),
                coverUrl,
                favorite.getPosition()
        );
    }
}
