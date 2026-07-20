package com.scriptles.cabinet.media.controller;

import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.media.dto.request.UpsertReviewRequest;
import com.scriptles.cabinet.media.dto.response.PopularReviewResponse;
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

import java.util.List;
import java.util.UUID;

@RestController
@Validated
@RequiredArgsConstructor
public class ReviewController {
    private final ReviewService reviewService;

    @GetMapping("/v1/reviews/popular")
    public List<PopularReviewResponse> findGloballyPopular(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "12") @Min(1) @Max(40) int limit
    ) {
        return reviewService.findGloballyPopular(user == null ? null : user.id(), limit);
    }

    @GetMapping("/v1/reviews/{reviewId}")
    public ReviewResponse findPublicById(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID reviewId
    ) {
        return reviewService.findPublicById(user == null ? null : user.id(), reviewId);
    }

    @GetMapping("/v1/media/{mediaId}/reviews")
    public PageResponse<ReviewResponse> findPublic(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID mediaId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size
    ) {
        return reviewService.findPublic(user == null ? null : user.id(), mediaId, page, size);
    }

    @GetMapping("/v1/media/{mediaId}/reviews/popular")
    public List<ReviewResponse> findPopular(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID mediaId
    ) {
        return reviewService.findPopular(user == null ? null : user.id(), mediaId);
    }

    @GetMapping("/v1/media/{mediaId}/reviews/recent")
    public List<ReviewResponse> findRecent(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID mediaId
    ) {
        return reviewService.findRecent(user == null ? null : user.id(), mediaId);
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
