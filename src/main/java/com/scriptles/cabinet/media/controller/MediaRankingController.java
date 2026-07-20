package com.scriptles.cabinet.media.controller;

import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.media.dto.response.MediaSearchItemResponse;
import com.scriptles.cabinet.media.dto.response.TrendingMediaResponse;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.service.MediaRankingService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/v1/media/rankings")
@RequiredArgsConstructor
public class MediaRankingController {
    private final MediaRankingService mediaRankingService;

    @GetMapping("/top-rated")
    public PageResponse<MediaSearchItemResponse> topRated(
            @RequestParam(required = false) MediaType type,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(40) int limit
    ) {
        return mediaRankingService.topRated(type, page, limit);
    }

    @GetMapping("/trending")
    public TrendingMediaResponse trending(
            @RequestParam(required = false) MediaType type,
            @RequestParam(defaultValue = "7") @Min(1) @Max(30) int days,
            @RequestParam(defaultValue = "12") @Min(1) @Max(40) int limit
    ) {
        return mediaRankingService.trending(type, days, limit);
    }
}
