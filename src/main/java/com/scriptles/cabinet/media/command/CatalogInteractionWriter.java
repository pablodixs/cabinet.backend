package com.scriptles.cabinet.media.command;

import com.scriptles.cabinet.lists.dto.request.AddMediaListItemRequest;
import com.scriptles.cabinet.lists.dto.response.MediaListItemResponse;
import com.scriptles.cabinet.lists.service.MediaListService;
import com.scriptles.cabinet.media.catalog.CatalogImportWriter;
import com.scriptles.cabinet.media.catalog.CatalogResolver;
import com.scriptles.cabinet.media.dto.request.MediaTarget;
import com.scriptles.cabinet.media.dto.response.TargetMediaLikeResponse;
import com.scriptles.cabinet.media.entity.Media;
import com.scriptles.cabinet.media.enums.CatalogStatus;
import com.scriptles.cabinet.media.service.MediaLikeService;
import com.scriptles.cabinet.user.dto.response.LibraryEntryResponse;
import com.scriptles.cabinet.user.enums.UserMediaStatus;
import com.scriptles.cabinet.user.service.UserMediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CatalogInteractionWriter {
    private final CatalogImportWriter importWriter;
    private final MediaLikeService mediaLikeService;
    private final UserMediaService userMediaService;
    private final MediaListService mediaListService;

    @Transactional
    public TargetMediaLikeResponse like(
            UUID userId,
            MediaTarget target,
            CatalogResolver.Resolution resolution
    ) {
        Media media = importWriter.materialize(target, resolution);
        mediaLikeService.like(userId, media.getId());
        return new TargetMediaLikeResponse(
                media.getId(),
                true,
                status(media),
                status(media) != CatalogStatus.READY
        );
    }

    @Transactional
    public LibraryEntryResponse upsertStatus(
            UUID userId,
            MediaTarget target,
            CatalogResolver.Resolution resolution,
            UserMediaStatus status
    ) {
        Media media = importWriter.materialize(target, resolution);
        return userMediaService.upsert(userId, media.getId(), status);
    }

    @Transactional
    public MediaListItemResponse addToList(
            UUID userId,
            UUID listId,
            MediaTarget target,
            CatalogResolver.Resolution resolution,
            String notes
    ) {
        Media media = importWriter.materialize(target, resolution);
        return mediaListService.addItem(
                userId, listId, new AddMediaListItemRequest(media.getId(), notes));
    }

    private CatalogStatus status(Media media) {
        return media.getCatalogStatus() == null ? CatalogStatus.READY : media.getCatalogStatus();
    }
}
