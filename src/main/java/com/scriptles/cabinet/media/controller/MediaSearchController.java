package com.scriptles.cabinet.media.controller;

import com.scriptles.cabinet.media.dto.response.MediaSearchPageResponse;
import com.scriptles.cabinet.media.enums.MediaSearchSort;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.service.MediaSearchService;
import com.scriptles.cabinet.media.translation.CatalogLocaleResolver;
import com.scriptles.cabinet.media.translation.LocalizedResponse;
import com.scriptles.cabinet.security.AuthenticatedUser;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@Validated
@RequestMapping("/v1/media")
@RequiredArgsConstructor
public class MediaSearchController {
    private final MediaSearchService mediaSearchService;
    private final CatalogLocaleResolver localeResolver;

    @GetMapping("/search")
    public ResponseEntity<MediaSearchPageResponse> search(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam @NotBlank @Size(min = 3) String query,
            @RequestParam(required = false) MediaType type,
            @RequestParam(defaultValue = "RELEVANCE") MediaSearchSort sort,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(40) int limit,
            @RequestParam(required = false) String locale,
            @RequestHeader(name = "Accept-Language", required = false) String acceptLanguage
    ) {
        String requestedLocale = localeResolver.resolve(locale, acceptLanguage).tag();
        MediaSearchPageResponse response = user == null
                ? mediaSearchService.search(query, type, sort, cursor, limit, requestedLocale)
                : mediaSearchService.search(query, type, sort, cursor, limit, user.id(), requestedLocale);
        return LocalizedResponse.ok(response, requestedLocale, locale == null || locale.isBlank());
    }
}
