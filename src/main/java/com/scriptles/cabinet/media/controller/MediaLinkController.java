package com.scriptles.cabinet.media.controller;

import com.scriptles.cabinet.media.dto.request.LinkWikidataRequest;
import com.scriptles.cabinet.media.dto.response.WikidataLinkResponse;
import com.scriptles.cabinet.media.service.WikidataLinkService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/media")
@RequiredArgsConstructor
public class MediaLinkController {
    private final WikidataLinkService wikidataLinkService;

    @PutMapping("/{mediaId}/wikidata")
    public WikidataLinkResponse linkWikidata(
            @PathVariable UUID mediaId,
            @RequestBody @Valid LinkWikidataRequest request
    ) {
        return wikidataLinkService.link(mediaId, request.wikidataId(), request.effectiveLanguage());
    }
}
