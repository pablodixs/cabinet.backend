package com.scriptles.cabinet.media.controller;

import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.media.dto.response.ArtistResponse;
import com.scriptles.cabinet.media.dto.response.ArtistWorkResponse;
import com.scriptles.cabinet.media.service.ArtistService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
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

    @GetMapping("/{artistId}")
    public ArtistResponse findDetails(@PathVariable UUID artistId) {
        return artistService.findDetails(artistId);
    }

    @GetMapping("/{artistId}/works")
    public PageResponse<ArtistWorkResponse> findWorks(
            @PathVariable UUID artistId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "24") @Min(1) @Max(40) int size
    ) {
        return artistService.findWorks(artistId, page, size);
    }
}
