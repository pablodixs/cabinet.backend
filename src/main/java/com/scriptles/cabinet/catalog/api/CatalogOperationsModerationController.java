package com.scriptles.cabinet.catalog.api;

import com.scriptles.cabinet.catalog.api.CatalogOperationsResponses.*;
import com.scriptles.cabinet.catalog.service.CatalogOperationsModerationService;
import com.scriptles.cabinet.common.api.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/moderation")
@PreAuthorize("@communityAuthorization.isModerator(authentication)")
@RequiredArgsConstructor
public class CatalogOperationsModerationController {
    private final CatalogOperationsModerationService service;

    @GetMapping("/operations")
    public PageResponse<OperationSummary> operations(@RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String trigger,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return service.listOperations(status, type, provider, trigger, query, page, size);
    }

    @GetMapping("/operations/{id}")
    public OperationDetails operation(@PathVariable UUID id) {
        return service.operation(id);
    }

    @GetMapping("/catalog-jobs")
    public PageResponse<JobSummary> jobs(@RequestParam(required = false) String status,
            @RequestParam(required = false, name = "type") String jobType,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return service.listJobs(status, jobType, provider, query, page, size);
    }

    @GetMapping("/catalog-jobs/queue")
    public QueueStatus queue() {
        return service.queueStatus();
    }

    @GetMapping("/catalog-jobs/{id}")
    public JobDetails job(@PathVariable UUID id) {
        return service.job(id);
    }

    @PostMapping("/catalog-jobs/{id}/retry")
    public java.util.Map<String, Object> retry(@PathVariable UUID id) {
        return java.util.Map.of("jobId", service.retry(id), "status", "PENDING");
    }

    @GetMapping("/external-catalog")
    public PageResponse<ExternalEntity> externalCatalog(@RequestParam(required = false) String provider,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return service.externalCatalog(provider, entityType, state, query, page, size);
    }
}
