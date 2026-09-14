package com.scriptles.cabinet.media.controller;

import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.media.dto.request.UpdateMediaMetadataRequest;
import com.scriptles.cabinet.media.dto.response.ModerationMediaResponse;
import com.scriptles.cabinet.media.service.MediaModerationService;
import com.scriptles.cabinet.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

@RestController
@Validated
@RequestMapping("/v1/moderation/media")
@PreAuthorize("@communityAuthorization.isModerator(authentication)")
@RequiredArgsConstructor
public class MediaModerationController {
    private final MediaModerationService mediaModerationService;

    @GetMapping
    public PageResponse<ModerationMediaResponse> findMedia(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size
    ) {
        return mediaModerationService.findMedia(query, page, size);
    }

    @PutMapping("/{mediaId}")
    public ModerationMediaResponse update(
            @PathVariable UUID mediaId,
            @RequestBody @Valid UpdateMediaMetadataRequest request,
            @AuthenticationPrincipal AuthenticatedUser editor
    ) {
        return mediaModerationService.update(mediaId, request, editor.id());
    }

    @PostMapping("/{mediaId}/refresh")
    public ResponseEntity<Void> refresh(@PathVariable UUID mediaId) {
        mediaModerationService.requestRefresh(mediaId);
        return ResponseEntity.accepted().build();
    }
}
