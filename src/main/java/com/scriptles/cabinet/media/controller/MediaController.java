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
import com.scriptles.cabinet.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
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
    public List<ExternalMediaResponse> search(
            @RequestParam(required = false) MediaType type,
            @RequestParam @NotBlank String query,
            @RequestParam(defaultValue = "pt-BR") @Pattern(regexp = "^[a-z]{2}(-[A-Z]{2})?$") String language,
            @RequestParam(defaultValue = "0") @Min(0) int startIndex,
            @RequestParam(defaultValue = "20") @Min(1) @Max(40) int maxResults
    ) {
        return externalMediaService.search(type, query, language, startIndex, maxResults);
    }

    @GetMapping("/{source}/{type}/{externalId}")
    public ExternalMediaDetailsResponse findDetails(
            @PathVariable ExternalSource source,
            @PathVariable MediaType type,
            @PathVariable @NotBlank String externalId,
            @RequestParam(defaultValue = "pt-BR") @Pattern(regexp = "^[a-z]{2}(-[A-Z]{2})?$") String language
    ) {
        return externalMediaService.findDetails(source, type, externalId, language);
    }

    @GetMapping("/{source}/{type}/{externalId}/relations")
    public RelatedMediaResponse findRelations(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable ExternalSource source,
            @PathVariable MediaType type,
            @PathVariable @NotBlank String externalId,
            @RequestParam(defaultValue = "pt-BR")
            @Pattern(regexp = "^[a-z]{2}(-[A-Z]{2})?$") String language,
            @RequestParam(defaultValue = "12") @Min(1) @Max(40) int maxResults
    ) {
        return user == null
                ? externalMediaService.findRelations(source, type, externalId, language, maxResults)
                : externalMediaService.findRelations(source, type, externalId, language, maxResults, user.id());
    }

    @GetMapping("/TMDB/SERIES/{externalId}/seasons/{seasonNumber}")
    public SeasonEpisodesResponse findSeasonEpisodes(
            @PathVariable @NotBlank String externalId,
            @PathVariable @Min(0) int seasonNumber,
            @RequestParam(defaultValue = "pt-BR") @Pattern(regexp = "^[a-z]{2}(-[A-Z]{2})?$") String language
    ) {
        return externalMediaService.findSeasonEpisodes(externalId, seasonNumber, language);
    }

    @PostMapping("/import")
    @ResponseStatus(HttpStatus.CREATED)
    public ExternalMediaResponse importMedia(
            @RequestBody @Valid ImportExternalMediaRequest request,
            @RequestParam(required = false) String locale,
            @RequestHeader(name = "Accept-Language", required = false) String acceptLanguage
    ) {
        String requestedLocale = localeResolver.resolve(locale, acceptLanguage).tag();
        return externalMediaService.importMedia(request, requestedLocale);
    }
}
