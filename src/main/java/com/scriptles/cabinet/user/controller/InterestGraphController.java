package com.scriptles.cabinet.user.controller;

import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.security.AuthenticatedUser;
import com.scriptles.cabinet.user.dto.request.UpsertInterestPreferenceRequest;
import com.scriptles.cabinet.user.dto.response.InterestOptionResponse;
import com.scriptles.cabinet.user.dto.response.InterestResponse;
import com.scriptles.cabinet.user.dto.response.RecommendationResponse;
import com.scriptles.cabinet.user.enums.InterestTargetType;
import com.scriptles.cabinet.user.service.InterestGraphService;
import com.scriptles.cabinet.user.service.RecommendationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/me")
@RequiredArgsConstructor
@Validated
public class InterestGraphController {
    private final InterestGraphService interestGraphService;
    private final RecommendationService recommendationService;

    @GetMapping("/recommendations")
    public RecommendationResponse recommendations(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) MediaType type,
            @RequestParam(defaultValue = "20") @Min(1) @Max(40) int limit) {
        return recommendationService.recommendations(user.id(), type, limit);
    }

    @GetMapping("/interests")
    public PageResponse<InterestResponse> interests(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam InterestTargetType targetType,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return interestGraphService.interests(user.id(), targetType, page, size);
    }

    @PutMapping("/interests")
    public InterestResponse upsert(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody @Valid UpsertInterestPreferenceRequest request) {
        return interestGraphService.upsert(user.id(), request);
    }

    @DeleteMapping("/interests")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam InterestTargetType targetType,
            @RequestParam @Size(min = 1, max = 100) String targetId) {
        interestGraphService.delete(user.id(), targetType, targetId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/interests/options")
    public List<InterestOptionResponse> options(
            @RequestParam InterestTargetType targetType,
            @RequestParam(defaultValue = "") @Size(max = 100) String query,
            @RequestParam(required = false) MediaType mediaType,
            @RequestParam(defaultValue = "20") @Min(1) @Max(40) int limit) {
        return interestGraphService.options(targetType, query, mediaType, limit);
    }
}
