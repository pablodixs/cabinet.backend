package com.scriptles.cabinet.status;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/status")
@RequiredArgsConstructor
public class StatusController {
    private final StatusService statusService;

    @GetMapping
    public PublicStatusResponse getStatus() {
        return statusService.getPublicStatus();
    }
}
