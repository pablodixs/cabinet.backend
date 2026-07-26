package com.scriptles.cabinet.media.controller;

import com.scriptles.cabinet.media.dto.response.HeaderSearchResponse;
import com.scriptles.cabinet.media.enums.HeaderSearchScope;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.service.HeaderSearchService;
import com.scriptles.cabinet.media.translation.CatalogLocaleResolver;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/v1/search")
@RequiredArgsConstructor
public class HeaderSearchController {
    private final HeaderSearchService headerSearchService;
    private final CatalogLocaleResolver localeResolver;

    @GetMapping("/header")
    public HeaderSearchResponse search(
            @RequestParam @NotBlank @Size(min = 2, max = 100) String query,
            @RequestParam(defaultValue = "ALL") HeaderSearchScope scope,
            @RequestParam(required = false) MediaType type,
            @RequestParam(required = false) String locale,
            @RequestHeader(name = "Accept-Language", required = false) String acceptLanguage
    ) {
        return headerSearchService.search(
                query,
                scope,
                type,
                localeResolver.resolve(locale, acceptLanguage).tag()
        );
    }
}
