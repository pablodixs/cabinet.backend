package com.scriptles.cabinet.media.controller;

import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.media.dto.response.ArtistResponse;
import com.scriptles.cabinet.media.dto.response.AwardPageResponse;
import com.scriptles.cabinet.media.dto.response.PersonWorkResponse;
import com.scriptles.cabinet.media.enums.AwardResult;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.service.ArtistService;
import com.scriptles.cabinet.media.service.AwardQueryService;
import com.scriptles.cabinet.media.service.PersonWorksService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Validated
@RequestMapping("/v1/people")
@RequiredArgsConstructor
public class PeopleController {
    private final ArtistService artistService;
    private final AwardQueryService awardQueryService;
    private final ObjectProvider<PersonWorksService> personWorksService;

    @GetMapping("/{personId}")
    public ArtistResponse findDetails(@PathVariable UUID personId) {
        return artistService.findDetails(personId);
    }

    @GetMapping("/{personId}/works")
    public PageResponse<PersonWorkResponse> findWorks(
            @PathVariable UUID personId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "24") @Min(1) @Max(40) int size,
            @RequestParam(defaultValue = "pt-BR") String language,
            @RequestParam(required = false) MediaType type
    ) {
        return personWorksService.getObject().findWorks(personId, page, size, language, type);
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
