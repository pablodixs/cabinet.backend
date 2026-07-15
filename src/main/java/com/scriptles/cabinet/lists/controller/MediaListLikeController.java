package com.scriptles.cabinet.lists.controller;

import com.scriptles.cabinet.lists.dto.response.MediaListLikeResponse;
import com.scriptles.cabinet.lists.service.MediaListLikeService;
import com.scriptles.cabinet.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/me/list-likes")
@RequiredArgsConstructor
public class MediaListLikeController {
    private final MediaListLikeService mediaListLikeService;

    @GetMapping("/{listId}")
    public MediaListLikeResponse find(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID listId
    ) {
        return mediaListLikeService.find(user.id(), listId);
    }

    @PutMapping("/{listId}")
    public MediaListLikeResponse like(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID listId
    ) {
        return mediaListLikeService.like(user.id(), listId);
    }

    @DeleteMapping("/{listId}")
    public MediaListLikeResponse unlike(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID listId
    ) {
        return mediaListLikeService.unlike(user.id(), listId);
    }
}
