package com.scriptles.cabinet.user.controller;

import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.security.AuthenticatedUser;
import com.scriptles.cabinet.user.dto.request.UpsertLibraryEntryRequest;
import com.scriptles.cabinet.user.dto.response.LibraryEntryResponse;
import com.scriptles.cabinet.user.dto.response.LibraryMediaResponse;
import com.scriptles.cabinet.user.enums.UserMediaStatus;
import com.scriptles.cabinet.user.service.UserMediaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@RestController
@RequestMapping("/v1/me/library")
@RequiredArgsConstructor
@Validated
public class MeLibraryController {
    private final UserMediaService userMediaService;

    @GetMapping
    public PageResponse<LibraryMediaResponse> findLibrary(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) UserMediaStatus status,
            @RequestParam(required = false) MediaType type,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size
    ) {
        return userMediaService.findLibrary(user.id(), status, type, page, size);
    }

    @GetMapping("/{mediaId}")
    public ResponseEntity<LibraryEntryResponse> find(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID mediaId
    ) {
        return userMediaService.find(user.id(), mediaId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping("/{mediaId}")
    public LibraryEntryResponse upsert(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID mediaId,
            @RequestBody @Valid UpsertLibraryEntryRequest request
    ) {
        return userMediaService.upsert(user.id(), mediaId, request.status());
    }

    @DeleteMapping("/{mediaId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID mediaId
    ) {
        userMediaService.delete(user.id(), mediaId);
        return ResponseEntity.noContent().build();
    }
}
