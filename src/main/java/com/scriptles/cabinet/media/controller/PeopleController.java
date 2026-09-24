package com.scriptles.cabinet.media.controller;

import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.media.dto.response.ArtistResponse;
import com.scriptles.cabinet.media.dto.response.AwardPageResponse;
import com.scriptles.cabinet.media.dto.response.PersonWorkResponse;
import com.scriptles.cabinet.media.enums.AwardResult;
import com.scriptles.cabinet.media.enums.ArtistWorkSort;
import com.scriptles.cabinet.media.enums.CreditRole;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.service.ArtistService;
import com.scriptles.cabinet.media.service.AwardQueryService;
import com.scriptles.cabinet.media.service.PersonWorksService;
import com.scriptles.cabinet.media.translation.CatalogLocaleResolver;
import com.scriptles.cabinet.media.translation.LocalizedResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.List;

@RestController
@Validated
@RequestMapping("/v1/people")
@RequiredArgsConstructor
public class PeopleController {
    private final ArtistService artistService;
    private final AwardQueryService awardQueryService;
    private final ObjectProvider<PersonWorksService> personWorksService;
    private final CatalogLocaleResolver localeResolver;

    @GetMapping("/{personId}")
    public ArtistResponse findDetails(@PathVariable UUID personId) {
        return artistService.findDetails(personId);
    }

    @GetMapping("/{personId}/works")
    public ResponseEntity<PageResponse<PersonWorkResponse>> findWorks(
            @PathVariable UUID personId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "24") @Min(1) @Max(40) int size,
            @RequestParam(required = false) @Pattern(regexp = "^[a-z]{2}(-[A-Z]{2})?$") String language,
            @RequestParam(required = false) MediaType type,
            @RequestParam(required = false) CreditRole role,
            @RequestParam(defaultValue = "RELEASE_DATE_DESC") ArtistWorkSort sort,
            @RequestParam(required = false) @Min(1800) @Max(2100) Integer year,
            @RequestHeader(name = "Accept-Language", required = false) String acceptLanguage
    ) {
        String requestedLocale = localeResolver.resolve(language, acceptLanguage).tag();
        PageResponse<PersonWorkResponse> works = role == null && year == null
                && sort == ArtistWorkSort.RELEASE_DATE_DESC
                ? personWorksService.getObject().findWorks(personId, page, size, requestedLocale, type)
                : personWorksService.getObject().findWorks(personId, page, size, requestedLocale,
                        type, role, sort, year);
        return LocalizedResponse.ok(
                works,
                requestedLocale,
                language == null || language.isBlank()
        );
    }

    @GetMapping("/{personId}/work-roles")
    public ResponseEntity<List<CreditRole>> findWorkRoles(
            @PathVariable UUID personId,
            @RequestParam(required = false) @Pattern(regexp = "^[a-z]{2}(-[A-Z]{2})?$") String language,
            @RequestHeader(name = "Accept-Language", required = false) String acceptLanguage
    ) {
        String requestedLocale = localeResolver.resolve(language, acceptLanguage).tag();
        return LocalizedResponse.ok(
                personWorksService.getObject().findRoles(personId, requestedLocale),
                requestedLocale,
                language == null || language.isBlank()
        );
    }

    @GetMapping("/{personId}/awards")
    public ResponseEntity<AwardPageResponse> findAwards(
            @PathVariable UUID personId,
            @RequestParam(required = false) AwardResult result,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        AwardPageResponse response = awardQueryService.findPerson(personId, result, page, size);
        return response.pendingWithoutData()
                ? ResponseEntity.accepted().header("Retry-After", "2").body(response)
                : ResponseEntity.ok(response);
    }
}
