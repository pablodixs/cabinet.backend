package com.scriptles.cabinet.media.command;

import com.scriptles.cabinet.lists.dto.response.MediaListItemResponse;
import com.scriptles.cabinet.media.catalog.CatalogResolver;
import com.scriptles.cabinet.media.dto.request.MediaTarget;
import com.scriptles.cabinet.media.dto.response.TargetMediaLikeResponse;
import com.scriptles.cabinet.user.dto.response.LibraryEntryResponse;
import com.scriptles.cabinet.user.enums.UserMediaStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class CatalogInteractionFacade {
    private final CatalogResolver resolver;
    private final CatalogInteractionWriter writer;

    public TargetMediaLikeResponse like(UUID userId, MediaTarget target) {
        return execute(target, resolution -> writer.like(userId, target, resolution));
    }

    public LibraryEntryResponse upsertStatus(UUID userId, MediaTarget target, UserMediaStatus status) {
        return execute(target, resolution -> writer.upsertStatus(userId, target, resolution, status));
    }

    public MediaListItemResponse addToList(
            UUID userId,
            UUID listId,
            MediaTarget target,
            String notes
    ) {
        return execute(target, resolution -> writer.addToList(userId, listId, target, resolution, notes));
    }

    private <T> T execute(
            MediaTarget target,
            Function<CatalogResolver.Resolution, T> command
    ) {
        CatalogResolver.Resolution resolution = resolver.resolve(target);
        try {
            return command.apply(resolution);
        } catch (DataIntegrityViolationException race) {
            if (target.mediaId() != null) throw race;
            CatalogResolver.Resolution winner = resolver.resolve(target);
            if (!winner.alreadyMaterialized()) throw race;
            return command.apply(winner);
        }
    }
}
