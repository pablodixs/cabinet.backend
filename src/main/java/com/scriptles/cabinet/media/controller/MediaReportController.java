package com.scriptles.cabinet.media.controller;

import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.media.dto.request.CreateMediaReportRequest;
import com.scriptles.cabinet.media.dto.request.ReviewMediaReportRequest;
import com.scriptles.cabinet.media.dto.response.MediaReportResponse;
import com.scriptles.cabinet.media.enums.MediaReportStatus;
import com.scriptles.cabinet.media.service.MediaReportService;
import com.scriptles.cabinet.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@Validated
@RequiredArgsConstructor
public class MediaReportController {
    private final MediaReportService mediaReportService;

    @PostMapping("/v1/media/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public MediaReportResponse create(
            @RequestBody @Valid CreateMediaReportRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return mediaReportService.create(request, user.id());
    }

    @GetMapping("/v1/moderation/media-reports")
    @PreAuthorize("@communityAuthorization.isModerator(authentication)")
    public PageResponse<MediaReportResponse> find(
            @RequestParam(required = false) MediaReportStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size
    ) {
        return mediaReportService.find(status, page, size);
    }

    @PostMapping("/v1/moderation/media-reports/{reportId}/review")
    @PreAuthorize("@communityAuthorization.isModerator(authentication)")
    public MediaReportResponse review(
            @PathVariable UUID reportId,
            @RequestBody @Valid ReviewMediaReportRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return mediaReportService.review(reportId, request, user.id());
    }
}
