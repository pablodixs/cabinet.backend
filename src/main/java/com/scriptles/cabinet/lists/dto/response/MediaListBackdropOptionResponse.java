package com.scriptles.cabinet.lists.dto.response;

import com.scriptles.cabinet.media.dto.response.ArtworkOptionResponse;

import java.util.UUID;

public record MediaListBackdropOptionResponse(
        UUID mediaId,
        String mediaTitle,
        String key,
        String url,
        String previewUrl,
        Integer width,
        Integer height
) {
    public static MediaListBackdropOptionResponse from(
            UUID mediaId,
            String mediaTitle,
            ArtworkOptionResponse option
    ) {
        return new MediaListBackdropOptionResponse(
                mediaId,
                mediaTitle,
                option.key(),
                option.url(),
                option.previewUrl() == null ? option.url() : option.previewUrl(),
                option.width(),
                option.height()
        );
    }
}
