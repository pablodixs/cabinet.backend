package com.scriptles.cabinet.lists.controller;

import com.scriptles.cabinet.lists.dto.request.AddMediaListItemRequest;
import com.scriptles.cabinet.lists.dto.request.CreateMediaListRequest;
import com.scriptles.cabinet.lists.dto.request.UpdateMediaListRequest;
import com.scriptles.cabinet.lists.dto.response.MediaListDetailsResponse;
import com.scriptles.cabinet.lists.dto.response.MediaListItemResponse;
import com.scriptles.cabinet.lists.dto.response.MediaListResponse;
import com.scriptles.cabinet.lists.service.MediaListService;
import com.scriptles.cabinet.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/me/lists")
@RequiredArgsConstructor
public class MediaListController {
    private final MediaListService mediaListService;

    @GetMapping
    public List<MediaListResponse> findMine(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return mediaListService.findMine(user.id());
    }

    @PostMapping
    public ResponseEntity<MediaListResponse> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody @Valid CreateMediaListRequest request
    ) {
        MediaListResponse response = mediaListService.create(user.id(), request);
        return ResponseEntity
                .created(URI.create("/v1/me/lists/" + response.id()))
                .body(response);
    }

    @GetMapping("/{listId}")
    public MediaListDetailsResponse findDetails(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID listId
    ) {
        return mediaListService.findDetails(user.id(), listId);
    }

    @PutMapping("/{listId}")
    public MediaListResponse update(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID listId,
            @RequestBody @Valid UpdateMediaListRequest request
    ) {
        return mediaListService.update(user.id(), listId, request);
    }

    @PostMapping("/{listId}/items")
    public ResponseEntity<MediaListItemResponse> addItem(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID listId,
            @RequestBody @Valid AddMediaListItemRequest request
    ) {
        MediaListItemResponse response = mediaListService.addItem(user.id(), listId, request);
        return ResponseEntity
                .created(URI.create("/v1/me/lists/" + listId + "/items/" + response.id()))
                .body(response);
    }

    @DeleteMapping("/{listId}/items/{itemId}")
    public ResponseEntity<Void> removeItem(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID listId,
            @PathVariable UUID itemId
    ) {
        mediaListService.removeItem(user.id(), listId, itemId);
        return ResponseEntity.noContent().build();
    }
}
