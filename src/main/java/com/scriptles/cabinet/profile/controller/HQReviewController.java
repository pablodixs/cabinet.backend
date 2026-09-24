package com.scriptles.cabinet.profile.controller;

import com.scriptles.cabinet.media.dto.request.UpsertReviewRequest;
import com.scriptles.cabinet.media.dto.response.ReviewResponse;
import com.scriptles.cabinet.media.service.ReviewService;
import com.scriptles.cabinet.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/v1/hq/{profileId}/reviews")
@RequiredArgsConstructor
public class HQReviewController {
    private final ReviewService reviews;

    @PutMapping("/{mediaId}")
    public ReviewResponse upsert(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID profileId, @PathVariable UUID mediaId,
            @Valid @RequestBody UpsertReviewRequest request) {
        return reviews.upsertHQ(actor.id(), profileId, mediaId, request);
    }
}
