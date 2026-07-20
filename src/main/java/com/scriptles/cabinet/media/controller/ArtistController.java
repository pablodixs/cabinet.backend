package com.scriptles.cabinet.media.controller;

import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.media.dto.response.ArtistResponse;
import com.scriptles.cabinet.media.dto.response.ArtistWorkResponse;
import com.scriptles.cabinet.media.dto.response.AwardPageResponse;
import com.scriptles.cabinet.media.enums.AwardResult;
import com.scriptles.cabinet.media.service.ArtistService;
import com.scriptles.cabinet.media.service.AwardQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Validated
@RequestMapping("/v1/artists")
@RequiredArgsConstructor
public class ArtistController {
    private final ArtistService artistService;
    private final AwardQueryService awardQueryService;

    @GetMapping("/{artistId}")
    public ResponseEntity<ArtistResponse> findDetails(@PathVariable UUID artistId) {
        return deprecated(artistService.findDetails(artistId), "/v1/people/" + artistId);
    }

    @GetMapping("/{artistId}/works")
    public ResponseEntity<PageResponse<ArtistWorkResponse>> findWorks(
            @PathVariable UUID artistId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "24") @Min(1) @Max(40) int size
    ) {
        return deprecated(
                artistService.findWorks(artistId, page, size),
                "/v1/people/" + artistId + "/works"
        );
    }

    @GetMapping("/{artistId}/awards")
    public ResponseEntity<AwardPageResponse> findAwards(
            @PathVariable UUID artistId,
            @RequestParam(required = false) AwardResult result,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        AwardPageResponse response = awardQueryService.findPerson(artistId, result, page, size);
        ResponseEntity.BodyBuilder builder = response.pendingWithoutData()
                ? ResponseEntity.accepted().header("Retry-After", "2")
                : ResponseEntity.ok();
        return builder
                .header("Deprecation", "true")
                .header("Link", "</v1/people/" + artistId + "/awards>; rel=\"successor-version\"")
                .body(response);
    }

    private <T> ResponseEntity<T> deprecated(T body, String successor) {
        return ResponseEntity.ok()
                .header("Deprecation", "true")
                .header("Link", "<" + successor + ">; rel=\"successor-version\"")
                .body(body);
    }
}
