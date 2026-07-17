package com.scriptles.cabinet.lists.controller;

import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.lists.dto.response.PublicMediaListDetailsResponse;
import com.scriptles.cabinet.lists.dto.response.PublicListSearchResponse;
import com.scriptles.cabinet.lists.service.MediaListService;
import com.scriptles.cabinet.security.AuthenticatedUser;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/lists")
@RequiredArgsConstructor
@Validated
public class PublicListController {
    private final MediaListService mediaListService;

    @GetMapping("/search")
    public PageResponse<PublicListSearchResponse> search(
            @RequestParam @Size(min = 3, max = 80) String query,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size
    ) {
        return mediaListService.searchPublic(query, page, size);
    }

    @GetMapping("/{listId}")
    public PublicMediaListDetailsResponse findDetails(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID listId
    ) {
        return mediaListService.findAccessibleDetails(
                user == null ? null : user.id(),
                listId
        );
    }
}
