package com.scriptles.cabinet.media.controller;

import com.scriptles.cabinet.media.dto.request.ImportExternalMediaRequest;
import com.scriptles.cabinet.media.dto.response.ExternalMediaResponse;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.service.ExternalMediaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
@RequestMapping("/v1/media/external")
@RequiredArgsConstructor
public class MediaController {
    private final ExternalMediaService externalMediaService;

    @GetMapping("/search")
    public List<ExternalMediaResponse> search(
            @RequestParam MediaType type,
            @RequestParam @NotBlank String query
    ) {
        return externalMediaService.search(type, query);
    }

    @PostMapping("/import")
    @ResponseStatus(HttpStatus.CREATED)
    public ExternalMediaResponse importMedia(@RequestBody @Valid ImportExternalMediaRequest request) {
        return externalMediaService.importMedia(request);
    }
}
