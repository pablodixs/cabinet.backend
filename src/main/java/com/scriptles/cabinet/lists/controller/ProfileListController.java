package com.scriptles.cabinet.lists.controller;

import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.lists.dto.response.PublicListSearchResponse;
import com.scriptles.cabinet.lists.service.MediaListService;
import com.scriptles.cabinet.security.AuthenticatedUser;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/users/{username}/lists")
@RequiredArgsConstructor
@Validated
public class ProfileListController {
    private final MediaListService mediaListService;

    @GetMapping
    public PageResponse<PublicListSearchResponse> findLists(
            @PathVariable String username,
            @AuthenticationPrincipal AuthenticatedUser viewer,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size
    ) {
        return mediaListService.findByOwner(
                username,
                viewer == null ? null : viewer.id(),
                page,
                size
        );
    }
}
