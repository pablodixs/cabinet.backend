package com.scriptles.cabinet.catalog.api;

import com.scriptles.cabinet.catalog.collection.CollectionType;
import com.scriptles.cabinet.catalog.service.CatalogPublicVersionService;
import com.scriptles.cabinet.catalog.service.CatalogQueryService;
import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.common.http.HttpCacheValidators;
import com.scriptles.cabinet.media.translation.CatalogLocaleResolver;
import com.scriptles.cabinet.media.translation.LocalizedResponse;
import com.scriptles.cabinet.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class CatalogController {
    private static final String PUBLIC_CACHE_CONTROL =
            "public, max-age=60, s-maxage=300, stale-while-revalidate=86400";

    private final CatalogQueryService service;
    private final CatalogPublicVersionService publicVersionService;
    private final CatalogLocaleResolver localeResolver;

    @GetMapping("/collections")
    public ResponseEntity<PageResponse<CollectionSummaryResponse>> collections(
            @RequestParam CollectionType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "POPULARITY") String sort,
            @RequestParam(required = false) String locale,
            @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage
    ) {
        String requested = localeResolver.resolve(locale, acceptLanguage).tag();
        return LocalizedResponse.ok(service.collections(type, page, size, sort, requested), requested,
                locale == null || locale.isBlank());
    }

    @GetMapping("/collections/{id}")
    public ResponseEntity<CollectionResponse> collection(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String locale,
            @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage,
            @RequestHeader(name = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch
    ) {
        String requested = localeResolver.resolve(locale, acceptLanguage).tag();
        String publicVersion = user == null ? publicVersionService.collectionVersion(id, requested).orElse(null) : null;
        String etag = publicVersion == null ? null
                : HttpCacheValidators.weakEtag("collection:" + id + ":" + requested, publicVersion);

        if (user == null && HttpCacheValidators.matchesIfNoneMatch(ifNoneMatch, etag)) {
            ResponseEntity.BodyBuilder notModified = ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .header(HttpHeaders.ETAG, etag)
                    .header(HttpHeaders.CACHE_CONTROL, PUBLIC_CACHE_CONTROL);
            addLanguageVariance(notModified, locale);
            addViewerVariance(notModified);
            return notModified.build();
        }

        CollectionResponse response = user == null && publicVersion != null
                ? service.collection(id, null, requested, publicVersion)
                : service.collection(id, user == null ? null : user.id(), requested);
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_LANGUAGE, response.resolvedLocale());
        addLanguageVariance(builder, locale);
        addViewerVariance(builder);
        if (user == null && "ACTIVE".equals(response.status()) && etag != null) {
            builder.header(HttpHeaders.CACHE_CONTROL, PUBLIC_CACHE_CONTROL)
                    .header(HttpHeaders.ETAG, etag);
        } else if (user != null) {
            builder.header(HttpHeaders.CACHE_CONTROL, "private, no-store");
        } else {
            builder.header(HttpHeaders.CACHE_CONTROL, "no-store, max-age=0");
        }
        return builder.body(response);
    }

    @GetMapping("/collections/slug/{slug}")
    public ResponseEntity<CollectionResponse> collectionSlug(
            @PathVariable String slug,
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String locale,
            @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage
    ) {
        String requested = localeResolver.resolve(locale, acceptLanguage).tag();
        CollectionResponse response = service.collectionBySlug(slug, user == null ? null : user.id(), requested);
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_LANGUAGE, response.resolvedLocale());
        addLanguageVariance(builder, locale);
        builder.header(HttpHeaders.CACHE_CONTROL, user == null ? "no-store, max-age=0" : "private, no-store");
        return builder.body(response);
    }

    @GetMapping("/franchises/{id}")
    public ResponseEntity<FranchiseResponse> franchise(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestHeader(name = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch
    ) {
        String publicVersion = user == null ? publicVersionService.franchiseVersion(id).orElse(null) : null;
        String etag = publicVersion == null ? null
                : HttpCacheValidators.weakEtag("franchise:" + id, publicVersion);
        if (user == null && HttpCacheValidators.matchesIfNoneMatch(ifNoneMatch, etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .header(HttpHeaders.ETAG, etag)
                    .header(HttpHeaders.CACHE_CONTROL, PUBLIC_CACHE_CONTROL)
                    .header(HttpHeaders.VARY, HttpHeaders.COOKIE)
                    .build();
        }

        FranchiseResponse response = user == null && publicVersion != null
                ? service.franchise(id, null, publicVersion)
                : service.franchise(id, user == null ? null : user.id());
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
        builder.header(HttpHeaders.VARY, HttpHeaders.COOKIE);
        if (user == null && "ACTIVE".equals(response.status()) && etag != null) {
            builder.header(HttpHeaders.CACHE_CONTROL, PUBLIC_CACHE_CONTROL)
                    .header(HttpHeaders.ETAG, etag);
        } else if (user != null) {
            builder.header(HttpHeaders.CACHE_CONTROL, "private, no-store");
        } else {
            builder.header(HttpHeaders.CACHE_CONTROL, "no-store, max-age=0");
        }
        return builder.body(response);
    }

    @GetMapping("/franchises/slug/{slug}")
    public ResponseEntity<FranchiseResponse> franchiseSlug(
            @PathVariable String slug,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        FranchiseResponse response = service.franchiseBySlug(slug, user == null ? null : user.id());
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, user == null ? "no-store, max-age=0" : "private, no-store")
                .body(response);
    }

    private void addLanguageVariance(ResponseEntity.BodyBuilder builder, String locale) {
        if (locale == null || locale.isBlank()) {
            builder.header(HttpHeaders.VARY, HttpHeaders.ACCEPT_LANGUAGE);
        }
    }

    private void addViewerVariance(ResponseEntity.BodyBuilder builder) {
        builder.header(HttpHeaders.VARY, HttpHeaders.COOKIE);
    }
}
