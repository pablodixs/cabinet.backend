package com.scriptles.cabinet.media.controller;

import com.scriptles.cabinet.media.dto.request.UpsertRatingRequest;
import com.scriptles.cabinet.media.dto.response.RatingResponse;
import com.scriptles.cabinet.media.service.RatingService;
import com.scriptles.cabinet.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/me/ratings")
@RequiredArgsConstructor
public class RatingController {
    private final RatingService ratingService;

    @GetMapping("/{mediaId}")
    public ResponseEntity<RatingResponse> find(@AuthenticationPrincipal AuthenticatedUser user,
                                               @PathVariable UUID mediaId) {
        return ratingService.find(user.id(), mediaId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping("/{mediaId}")
    public RatingResponse upsert(@AuthenticationPrincipal AuthenticatedUser user,
                                 @PathVariable UUID mediaId,
                                 @RequestBody @Valid UpsertRatingRequest request) {
        return ratingService.upsert(user.id(), mediaId, request);
    }

    @DeleteMapping("/{mediaId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser user,
                                       @PathVariable UUID mediaId) {
        ratingService.delete(user.id(), mediaId);
        return ResponseEntity.noContent().build();
    }
}
