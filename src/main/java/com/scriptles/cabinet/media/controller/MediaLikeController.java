package com.scriptles.cabinet.media.controller;

import com.scriptles.cabinet.media.dto.response.MediaLikeResponse;
import com.scriptles.cabinet.media.service.MediaLikeService;
import com.scriptles.cabinet.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/me/likes")
@RequiredArgsConstructor
@Validated
public class MediaLikeController {
    private final MediaLikeService mediaLikeService;

    @GetMapping("/{mediaId}")
    public MediaLikeResponse find(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID mediaId
    ) {
        return mediaLikeService.find(user.id(), mediaId);
    }

    @PutMapping("/{mediaId}")
    public MediaLikeResponse like(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID mediaId
    ) {
        return mediaLikeService.like(user.id(), mediaId);
    }

    @DeleteMapping("/{mediaId}")
    public ResponseEntity<Void> unlike(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID mediaId
    ) {
        mediaLikeService.unlike(user.id(), mediaId);
        return ResponseEntity.noContent().build();
    }
}
