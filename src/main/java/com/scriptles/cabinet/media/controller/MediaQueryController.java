package com.scriptles.cabinet.media.controller;

import com.scriptles.cabinet.media.dto.response.ExternalMediaDetailsResponse;
import com.scriptles.cabinet.media.service.MediaQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/media")
@RequiredArgsConstructor
public class MediaQueryController {
    private final MediaQueryService mediaQueryService;

    @GetMapping("/{mediaId}")
    public ExternalMediaDetailsResponse findDetails(@PathVariable UUID mediaId) {
        return mediaQueryService.findDetails(mediaId);
    }
}
