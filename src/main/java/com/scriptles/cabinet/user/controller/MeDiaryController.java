package com.scriptles.cabinet.user.controller;

import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.security.AuthenticatedUser;
import com.scriptles.cabinet.user.dto.request.CreateDiaryEntryRequest;
import com.scriptles.cabinet.user.dto.response.DiaryEntryResponse;
import com.scriptles.cabinet.user.service.DiaryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/me/diary")
@RequiredArgsConstructor
@Validated
public class MeDiaryController {
    private final DiaryService diaryService;

    @GetMapping
    public PageResponse<DiaryEntryResponse> find(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size
    ) {
        return diaryService.findMine(user.id(), page, size);
    }

    @PostMapping
    public ResponseEntity<DiaryEntryResponse> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody @Valid CreateDiaryEntryRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(diaryService.create(user.id(), request));
    }

    @DeleteMapping("/{entryId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID entryId
    ) {
        diaryService.delete(user.id(), entryId);
        return ResponseEntity.noContent().build();
    }
}
