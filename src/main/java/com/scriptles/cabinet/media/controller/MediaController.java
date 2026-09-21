package com.scriptles.cabinet.media.controller;

import com.scriptles.cabinet.media.dto.request.ImportExternalMediaRequest;
import com.scriptles.cabinet.media.dto.response.ExternalMediaDetailsResponse;
import com.scriptles.cabinet.media.dto.response.ExternalMediaResponse;
import com.scriptles.cabinet.media.dto.response.RelatedMediaResponse;
import com.scriptles.cabinet.media.dto.response.SeasonEpisodesResponse;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.service.ExternalMediaService;
import com.scriptles.cabinet.media.translation.CatalogLocaleResolver;
import com.scriptles.cabinet.media.translation.LocalizedResponse;
import com.scriptles.cabinet.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.List;

@RestController
@Validated
@RequestMapping("/v1/media/external")
@RequiredArgsConstructor
public class MediaController {
    private final ExternalMediaService externalMediaService;
    private final CatalogLocaleResolver localeResolver;

    @GetMapping("/search")
    public ResponseEntity<List<ExternalMediaResponse>> search(
            @RequestParam(required = false) MediaType type,
            @RequestParam @NotBlank String query,
            @RequestParam(required = false) @Pattern(regexp = "^[a-z]{2}(-[A-Z]{2})?$") String language,
            @RequestParam(defaultValue = "0") @Min(0) int startIndex,
            @RequestParam(defaultValue = "20") @Min(1) @Max(40) int maxResults,
            @RequestHeader(name = "Accept-Language", required = false) String acceptLanguage
    ) {
        String requestedLocale = localeResolver.resolve(language, acceptLanguage).tag();
        return LocalizedResponse.ok(
                externalMediaService.search(type, query, requestedLocale, startIndex, maxResults),
                requestedLocale,
                language == null || language.isBlank()
        );
    }

    @GetMapping("/{source}/{type}/{externalId}")
    public ResponseEntity<ExternalMediaDetailsResponse> findDetails(
            @PathVariable ExternalSource source,
            @PathVariable MediaType type,
            @PathVariable @NotBlank String externalId,
            @RequestParam(required = false) @Pattern(regexp = "^[a-z]{2}(-[A-Z]{2})?$") String language,
            @RequestHeader(name = "Accept-Language", required = false) String acceptLanguage
    ) {
        String requestedLocale = localeResolver.resolve(language, acceptLanguage).tag();
        return LocalizedResponse.ok(
                externalMediaService.findDetails(source, type, externalId, requestedLocale),
                requestedLocale,
                language == null || language.isBlank()
        );
    }

    @GetMapping("/{source}/{type}/{externalId}/relations")
    public ResponseEntity<RelatedMediaResponse> findRelations(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable ExternalSource source,
            @PathVariable MediaType type,
            @PathVariable @NotBlank String externalId,
            @RequestParam(required = false)
            @Pattern(regexp = "^[a-z]{2}(-[A-Z]{2})?$") String language,
            @RequestParam(defaultValue = "12") @Min(1) @Max(40) int maxResults,
            @RequestHeader(name = "Accept-Language", required = false) String acceptLanguage
    ) {
        String requestedLocale = localeResolver.resolve(language, acceptLanguage).tag();
        RelatedMediaResponse response = user == null
                ? externalMediaService.findRelations(source, type, externalId, requestedLocale, maxResults)
                : externalMediaService.findRelations(
                        source, type, externalId, requestedLocale, maxResults, user.id());
        return LocalizedResponse.ok(response, requestedLocale, language == null || language.isBlank());
    }

    @GetMapping("/TMDB/SERIES/{externalId}/seasons/{seasonNumber}")
    public ResponseEntity<SeasonEpisodesResponse> findSeasonEpisodes(
            @PathVariable @NotBlank String externalId,
            @PathVariable @Min(0) int seasonNumber,
            @RequestParam(required = false) @Pattern(regexp = "^[a-z]{2}(-[A-Z]{2})?$") String language,
            @RequestHeader(name = "Accept-Language", required = false) String acceptLanguage
    ) {
        String requestedLocale = localeResolver.resolve(language, acceptLanguage).tag();
        return LocalizedResponse.ok(
                externalMediaService.findSeasonEpisodes(externalId, seasonNumber, requestedLocale),
                requestedLocale,
                language == null || language.isBlank()
        );
    }

    @PostMapping("/import")
    public ResponseEntity<ExternalMediaResponse> importMedia(
            @RequestBody @Valid ImportExternalMediaRequest request,
            @RequestParam(required = false) String locale,
            @RequestHeader(name = "Accept-Language", required = false) String acceptLanguage
    ) {
        String requestedLocale = localeResolver.resolve(locale, acceptLanguage).tag();
        return LocalizedResponse.created(
                externalMediaService.importMedia(request, requestedLocale), requestedLocale);
    }
}
