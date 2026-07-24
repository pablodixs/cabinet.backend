package com.scriptles.cabinet.media.controller;

import com.scriptles.cabinet.lists.dto.request.AddTargetMediaListItemRequest;
import com.scriptles.cabinet.lists.dto.response.MediaListItemResponse;
import com.scriptles.cabinet.media.command.CatalogInteractionFacade;
import com.scriptles.cabinet.media.command.RatingInteractionFacade;
import com.scriptles.cabinet.media.dto.request.MediaTargetRequest;
import com.scriptles.cabinet.media.dto.request.UpsertExternalRatingRequest;
import com.scriptles.cabinet.media.dto.response.RatingResponse;
import com.scriptles.cabinet.media.dto.response.TargetMediaLikeResponse;
import com.scriptles.cabinet.security.AuthenticatedUser;
import com.scriptles.cabinet.user.dto.request.UpsertTargetLibraryEntryRequest;
import com.scriptles.cabinet.user.dto.response.LibraryEntryResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class CatalogInteractionController {
    private final RatingInteractionFacade ratingFacade;
    private final CatalogInteractionFacade interactionFacade;

    @PutMapping("/ratings")
    public RatingResponse rate(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody @Valid UpsertExternalRatingRequest request
    ) {
        return ratingFacade.upsert(user.id(), request.media(), request.rating());
    }

    @PutMapping("/likes")
    public TargetMediaLikeResponse like(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody @Valid MediaTargetRequest request
    ) {
        return interactionFacade.like(user.id(), request.media());
    }

    @PutMapping("/library")
    public LibraryEntryResponse upsertStatus(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody @Valid UpsertTargetLibraryEntryRequest request
    ) {
        return interactionFacade.upsertStatus(user.id(), request.media(), request.status());
    }

    @PostMapping("/lists/{listId}/items")
    public MediaListItemResponse addToList(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID listId,
            @RequestBody @Valid AddTargetMediaListItemRequest request
    ) {
        return interactionFacade.addToList(user.id(), listId, request.media(), request.notes());
    }
}
