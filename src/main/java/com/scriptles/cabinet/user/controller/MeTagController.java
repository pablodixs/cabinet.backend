package com.scriptles.cabinet.user.controller;

import com.scriptles.cabinet.security.AuthenticatedUser;
import com.scriptles.cabinet.user.dto.request.CreateTagRequest;
import com.scriptles.cabinet.user.dto.request.UpdateMediaTagsRequest;
import com.scriptles.cabinet.user.dto.response.ProfileTagResponse;
import com.scriptles.cabinet.user.service.UserTagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/me/tags")
@RequiredArgsConstructor
public class MeTagController {
    private final UserTagService tagService;

    @GetMapping
    public List<ProfileTagResponse> findMine(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return tagService.findMine(user.id());
    }

    @PostMapping
    public ResponseEntity<ProfileTagResponse> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody @Valid CreateTagRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tagService.create(user.id(), request.name()));
    }

    @GetMapping("/media/{mediaId}")
    public List<String> findMediaTags(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID mediaId) {
        return tagService.findMediaTags(user.id(), mediaId);
    }

    @PutMapping("/media/{mediaId}")
    public List<String> replaceMediaTags(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID mediaId,
            @RequestBody @Valid UpdateMediaTagsRequest request) {
        return tagService.replaceMediaTags(user.id(), mediaId, request.tags());
    }
}
