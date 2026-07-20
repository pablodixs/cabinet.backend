package com.scriptles.cabinet.media.controller;

import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.media.dto.request.CreateAwardRequest;
import com.scriptles.cabinet.media.dto.request.UpdateAwardRequest;
import com.scriptles.cabinet.media.dto.response.ModerationAwardResponse;
import com.scriptles.cabinet.media.enums.AwardSubjectType;
import com.scriptles.cabinet.media.service.AwardModerationService;
import com.scriptles.cabinet.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/v1/moderation/awards")
@PreAuthorize("@communityAuthorization.isModerator(authentication)")
@RequiredArgsConstructor
public class AwardModerationController {
    private final AwardModerationService service;

    @GetMapping
    public PageResponse<ModerationAwardResponse> find(
            @RequestParam AwardSubjectType subjectType,
            @RequestParam UUID subjectId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return service.find(subjectType, subjectId, page, size);
    }

    @PostMapping
    public ResponseEntity<ModerationAwardResponse> create(
            @RequestBody @Valid CreateAwardRequest request,
            @AuthenticationPrincipal AuthenticatedUser editor
    ) {
        ModerationAwardResponse response = service.create(request, editor.id());
        return ResponseEntity.created(URI.create("/v1/moderation/awards/" + response.id())).body(response);
    }

    @PutMapping("/{awardId}")
    public ModerationAwardResponse update(
            @PathVariable UUID awardId,
            @RequestBody @Valid UpdateAwardRequest request,
            @AuthenticationPrincipal AuthenticatedUser editor
    ) {
        return service.update(awardId, request, editor.id());
    }

    @DeleteMapping("/{awardId}")
    public ResponseEntity<Void> hide(
            @PathVariable UUID awardId,
            @AuthenticationPrincipal AuthenticatedUser editor
    ) {
        service.hide(awardId, editor.id());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{awardId}/reset-to-source")
    public ModerationAwardResponse resetToSource(
            @PathVariable UUID awardId,
            @AuthenticationPrincipal AuthenticatedUser editor
    ) {
        return service.resetToSource(awardId, editor.id());
    }
}
