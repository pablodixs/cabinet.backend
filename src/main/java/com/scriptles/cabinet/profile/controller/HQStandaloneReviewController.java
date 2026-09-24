package com.scriptles.cabinet.profile.controller;

import com.scriptles.cabinet.profile.service.HQOperatorService;
import com.scriptles.cabinet.profile.service.HQReviewService;
import com.scriptles.cabinet.security.AuthenticatedHQ;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController @RequestMapping("/v1/hq-console/reviews") @RequiredArgsConstructor
public class HQStandaloneReviewController {
    private final HQReviewService reviews;
    private final HQOperatorService operators;
    @GetMapping public List<HQReviewService.ReviewView> mine(@AuthenticationPrincipal AuthenticatedHQ actor) { return reviews.mine(operators.refresh(actor.operatorId())); }
    @PutMapping("/{mediaId}") public HQReviewService.ReviewView upsert(@AuthenticationPrincipal AuthenticatedHQ actor, @PathVariable UUID mediaId, @Valid @RequestBody HQReviewService.ReviewInput input) { return reviews.upsert(operators.refresh(actor.operatorId()), mediaId, input); }
    @DeleteMapping("/{mediaId}") public void delete(@AuthenticationPrincipal AuthenticatedHQ actor, @PathVariable UUID mediaId) { reviews.delete(operators.refresh(actor.operatorId()), mediaId); }
}
