package com.scriptles.cabinet.user.controller;

import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.security.AuthenticatedUser;
import com.scriptles.cabinet.user.dto.request.ResolveLetterboxdImportItemRequest;
import com.scriptles.cabinet.user.dto.response.ActiveLetterboxdImportResponse;
import com.scriptles.cabinet.user.dto.response.LetterboxdImportItemResponse;
import com.scriptles.cabinet.user.dto.response.LetterboxdImportJobResponse;
import com.scriptles.cabinet.user.importer.LetterboxdImportItemState;
import com.scriptles.cabinet.user.importer.LetterboxdImportService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/v1/me/imports")
@RequiredArgsConstructor
@Validated
public class MeImportController {
    private final LetterboxdImportService importService;

    @PostMapping(value = "/letterboxd", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public LetterboxdImportJobResponse upload(
            @org.springframework.security.core.annotation.AuthenticationPrincipal AuthenticatedUser user,
            @RequestPart("file") MultipartFile file
    ) {
        return importService.start(user.id(), file);
    }

    @GetMapping("/letterboxd/active")
    public ActiveLetterboxdImportResponse active(
            @org.springframework.security.core.annotation.AuthenticationPrincipal AuthenticatedUser user
    ) {
        return importService.findActive(user.id());
    }

    @GetMapping("/{jobId}")
    public LetterboxdImportJobResponse find(
            @org.springframework.security.core.annotation.AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID jobId
    ) {
        return importService.find(user.id(), jobId);
    }

    @GetMapping("/{jobId}/items")
    public PageResponse<LetterboxdImportItemResponse> items(
            @org.springframework.security.core.annotation.AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID jobId,
            @RequestParam(required = false) LetterboxdImportItemState state,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size
    ) {
        return importService.items(user.id(), jobId, state, page, size);
    }

    @PutMapping("/{jobId}/items/{itemId}/resolution")
    public LetterboxdImportItemResponse resolve(
            @org.springframework.security.core.annotation.AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID jobId,
            @PathVariable UUID itemId,
            @RequestBody ResolveLetterboxdImportItemRequest request
    ) {
        return importService.resolve(user.id(), jobId, itemId, request);
    }

    @PostMapping("/{jobId}/confirm")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public LetterboxdImportJobResponse confirm(
            @org.springframework.security.core.annotation.AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID jobId
    ) {
        return importService.confirm(user.id(), jobId);
    }

    @PostMapping("/{jobId}/retry-failures")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public LetterboxdImportJobResponse retryFailures(
            @org.springframework.security.core.annotation.AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID jobId
    ) {
        return importService.retryFailed(user.id(), jobId);
    }

    @PostMapping("/{jobId}/cancel")
    public LetterboxdImportJobResponse cancel(
            @org.springframework.security.core.annotation.AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID jobId
    ) {
        return importService.cancel(user.id(), jobId);
    }

    @GetMapping(value = "/{jobId}/failures.csv", produces = "text/csv")
    public ResponseEntity<byte[]> failures(
            @org.springframework.security.core.annotation.AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID jobId
    ) {
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=letterboxd-import-failures.csv")
                .body(importService.failures(user.id(), jobId));
    }
}
