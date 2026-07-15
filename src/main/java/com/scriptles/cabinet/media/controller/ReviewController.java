package com.scriptles.cabinet.media.controller;

import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.media.dto.request.UpsertReviewRequest;
import com.scriptles.cabinet.media.dto.response.ReviewResponse;
import com.scriptles.cabinet.media.service.ReviewService;
import com.scriptles.cabinet.security.AuthenticatedUser;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Validated
@RequiredArgsConstructor
public class ReviewController {
    private final ReviewService reviewService;

    @GetMapping("/v1/media/{mediaId}/reviews")
    public PageResponse<ReviewResponse> findPublic(
            @PathVariable UUID mediaId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size
    ) {
        return reviewService.findPublic(mediaId, page, size);
    }

    @GetMapping("/v1/me/reviews/{mediaId}")
    public ResponseEntity<ReviewResponse> findMine(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID mediaId
    ) {
        return reviewService.findMine(user.id(), mediaId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping("/v1/me/reviews/{mediaId}")
    public ReviewResponse upsert(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID mediaId,
            @RequestBody @Valid UpsertReviewRequest request
    ) {
        return reviewService.upsert(user.id(), mediaId, request);
    }

    @DeleteMapping("/v1/me/reviews/{mediaId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID mediaId
    ) {
        reviewService.delete(user.id(), mediaId);
        return ResponseEntity.noContent().build();
    }
}
