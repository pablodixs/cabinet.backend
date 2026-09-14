package com.scriptles.cabinet.user.dto.response;

import com.scriptles.cabinet.media.entity.ExternalReference;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.user.entity.UserAlbumRotation;

import java.time.Instant;
import java.util.UUID;

public record UserAlbumRotationResponse(
        UUID id,
        UUID mediaId,
        MediaType type,
        String title,
        String creator,
        String coverUrl,
        ExternalSource source,
        String externalId,
        int position,
        Instant addedAt
) {
    public static UserAlbumRotationResponse from(
            UserAlbumRotation rotation,
            ExternalReference reference,
            String coverUrl,
            String creator
    ) {
        Media album = rotation.getAlbum();
        return new UserAlbumRotationResponse(
                rotation.getId(), album.getId(), album.getType(), album.getTitle(), creator,
                coverUrl, reference == null ? null : reference.getSource(),
                reference == null ? null : reference.getExternalId(),
                rotation.getPosition(), rotation.getAddedAt()
        );
    }
}
