package com.scriptles.cabinet.media.controller;

import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.media.dto.response.AnticipatedMediaResponse;
import com.scriptles.cabinet.media.dto.response.MediaSearchItemResponse;
import com.scriptles.cabinet.media.dto.response.TrendingMediaResponse;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.service.MediaRankingService;
import com.scriptles.cabinet.media.translation.CatalogLocaleResolver;
import com.scriptles.cabinet.media.translation.LocalizedResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/v1/media/rankings")
@RequiredArgsConstructor
public class MediaRankingController {
    private final MediaRankingService mediaRankingService;
    private final CatalogLocaleResolver localeResolver;

    @GetMapping("/top-rated")
    public ResponseEntity<PageResponse<MediaSearchItemResponse>> topRated(
            @RequestParam(required = false) MediaType type,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(40) int limit,
            @RequestParam(required = false) String locale,
            @RequestHeader(name = "Accept-Language", required = false) String acceptLanguage
    ) {
        String requestedLocale = localeResolver.resolve(locale, acceptLanguage).tag();
        return LocalizedResponse.ok(
                mediaRankingService.topRated(type, page, limit, requestedLocale),
                requestedLocale,
                locale == null || locale.isBlank()
        );
    }

    @GetMapping("/trending")
    public ResponseEntity<TrendingMediaResponse> trending(
            @RequestParam(required = false) MediaType type,
            @RequestParam(defaultValue = "7") @Min(1) @Max(30) int days,
            @RequestParam(defaultValue = "12") @Min(1) @Max(40) int limit,
            @RequestParam(required = false) String locale,
            @RequestHeader(name = "Accept-Language", required = false) String acceptLanguage
    ) {
        String requestedLocale = localeResolver.resolve(locale, acceptLanguage).tag();
        return LocalizedResponse.ok(
                mediaRankingService.trending(type, days, limit, requestedLocale),
                requestedLocale,
                locale == null || locale.isBlank()
        );
    }

    @GetMapping("/anticipated")
    public ResponseEntity<AnticipatedMediaResponse> anticipated(
            @RequestParam(defaultValue = "6") @Min(1) @Max(40) int limit,
            @RequestParam(required = false) String locale,
            @RequestHeader(name = "Accept-Language", required = false) String acceptLanguage
    ) {
        String requestedLocale = localeResolver.resolve(locale, acceptLanguage).tag();
        return LocalizedResponse.ok(
                mediaRankingService.anticipated(limit, requestedLocale),
                requestedLocale,
                locale == null || locale.isBlank()
        );
    }
}
