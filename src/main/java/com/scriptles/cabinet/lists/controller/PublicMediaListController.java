package com.scriptles.cabinet.lists.controller;

import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.lists.dto.response.PublicMediaListResponse;
import com.scriptles.cabinet.lists.service.MediaListService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
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
public class PublicMediaListController {
    private final MediaListService mediaListService;

    @GetMapping("/{mediaId}/lists")
    public PageResponse<PublicMediaListResponse> findPublicLists(
            @PathVariable UUID mediaId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size
    ) {
        return mediaListService.findPublicByMedia(mediaId, page, size);
    }
}
