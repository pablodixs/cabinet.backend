package com.scriptles.cabinet.media.controller;

import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.media.dto.response.ExternalMediaDetailsResponse;
import com.scriptles.cabinet.media.dto.response.MediaExternalInfoResponse;
import com.scriptles.cabinet.media.dto.response.MoreByResponse;
import com.scriptles.cabinet.media.enums.CreditRole;
import com.scriptles.cabinet.media.service.MediaExternalInfoService;
import com.scriptles.cabinet.media.service.MediaQueryService;
import com.scriptles.cabinet.media.service.MoreByService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Validated
@RequestMapping("/v1/media")
@RequiredArgsConstructor
public class MediaQueryController {
    private final MediaQueryService mediaQueryService;
    private final MediaExternalInfoService mediaExternalInfoService;
    private final MoreByService moreByService;

    @GetMapping("/{mediaId}")
    public ExternalMediaDetailsResponse findDetails(@PathVariable UUID mediaId) {
        return mediaQueryService.findDetails(mediaId);
    }

    @GetMapping("/{mediaId}/credits")
    public PageResponse<ExternalMediaDetailsResponse.CreditResponse> findCredits(
            @PathVariable UUID mediaId,
            @RequestParam CreditRole role,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(40) int limit
    ) {
        return mediaQueryService.findCredits(mediaId, role, page, limit);
    }

    @GetMapping("/{mediaId}/more-by")
    public MoreByResponse findMoreBy(
            @PathVariable UUID mediaId,
            @RequestParam(defaultValue = "pt-BR")
            @Pattern(regexp = "^[a-z]{2}(-[A-Z]{2})?$") String language,
            @RequestParam(defaultValue = "12") @Min(1) @Max(40) int limit
    ) {
        return moreByService.find(mediaId, language, limit);
    }

    @GetMapping("/{mediaId}/external-info")
    public ResponseEntity<MediaExternalInfoResponse> findExternalInfo(
            @PathVariable UUID mediaId,
            @RequestParam(defaultValue = "BR")
            @Pattern(regexp = "^[A-Za-z]{2}$") String country
    ) {
        MediaExternalInfoResponse response = mediaExternalInfoService.find(mediaId, country);
        if (response.pendingWithoutData()) {
            return ResponseEntity.accepted()
                    .header("Retry-After", "2")
                    .body(response);
        }
        return ResponseEntity.ok(response);
    }
}
