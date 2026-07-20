package com.scriptles.cabinet.media.controller;

import com.scriptles.cabinet.media.dto.request.UpsertUserMediaArtworkRequest;
import com.scriptles.cabinet.media.dto.response.ArtworkOptionsResponse;
import com.scriptles.cabinet.media.service.UserMediaArtworkService;
import com.scriptles.cabinet.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/me/media")
@RequiredArgsConstructor
public class UserMediaArtworkController {
    private final UserMediaArtworkService artworkService;

    @GetMapping("/{mediaId}/artwork-options")
    public ArtworkOptionsResponse findOptions(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID mediaId
    ) {
        return artworkService.findOptions(user.id(), mediaId);
    }

    @PutMapping("/{mediaId}/artwork")
    public ArtworkOptionsResponse upsert(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID mediaId,
            @RequestBody @Valid UpsertUserMediaArtworkRequest request
    ) {
        return artworkService.upsert(user.id(), mediaId, request);
    }

    @DeleteMapping("/{mediaId}/artwork")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID mediaId
    ) {
        artworkService.delete(user.id(), mediaId);
        return ResponseEntity.noContent().build();
    }
}
