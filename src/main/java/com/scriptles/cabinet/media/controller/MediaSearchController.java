package com.scriptles.cabinet.media.controller;

import com.scriptles.cabinet.media.dto.response.MediaSearchPageResponse;
import com.scriptles.cabinet.media.enums.MediaSearchSort;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.service.MediaSearchService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/v1/media")
@RequiredArgsConstructor
public class MediaSearchController {
    private final MediaSearchService mediaSearchService;

    @GetMapping("/search")
    public MediaSearchPageResponse search(
            @RequestParam @NotBlank @Size(min = 3) String query,
            @RequestParam(required = false) MediaType type,
            @RequestParam(defaultValue = "RELEVANCE") MediaSearchSort sort,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(40) int limit
    ) {
        return mediaSearchService.search(query, type, sort, cursor, limit);
    }
}
