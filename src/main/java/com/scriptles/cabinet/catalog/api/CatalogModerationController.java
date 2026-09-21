package com.scriptles.cabinet.catalog.api;

import com.scriptles.cabinet.catalog.entity.Collection;
import com.scriptles.cabinet.catalog.entity.Franchise;
import com.scriptles.cabinet.catalog.service.CatalogJobOrchestrationService;
import com.scriptles.cabinet.catalog.service.CatalogModerationService;
import com.scriptles.cabinet.catalog.service.CollectionAuditModerationService;
import com.scriptles.cabinet.catalog.service.TmdbCollectionDiscoveryService;
import com.scriptles.cabinet.common.api.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/v1/moderation")
@PreAuthorize("@communityAuthorization.isModerator(authentication)")
@RequiredArgsConstructor
public class CatalogModerationController {
    private final CatalogModerationService service;
    private final TmdbCollectionDiscoveryService collectionDiscoveryService;
    private final CatalogJobOrchestrationService jobOrchestrationService;
    private final CollectionAuditModerationService collectionAuditService;

    @PostMapping("/collections")
    public ResponseEntity<Void> createCollection(@RequestBody @Valid UpsertCollectionRequest request) {
        Collection collection = service.saveCollection(null, request);
        return ResponseEntity.created(URI.create("/v1/collections/" + collection.getId())).build();
    }

    @PutMapping("/collections/{id}")
    public void updateCollection(@PathVariable UUID id, @RequestBody @Valid UpsertCollectionRequest request) {
        service.saveCollection(id, request);
    }

    @PostMapping("/franchises")
    public ResponseEntity<Void> createFranchise(@RequestBody @Valid UpsertFranchiseRequest request) {
        Franchise franchise = service.saveFranchise(null, request);
        return ResponseEntity.created(URI.create("/v1/franchises/" + franchise.getId())).build();
    }

    @PutMapping("/franchises/{id}")
    public void updateFranchise(@PathVariable UUID id, @RequestBody @Valid UpsertFranchiseRequest request) {
        service.saveFranchise(id, request);
    }

    @PutMapping("/collections/{id}/media")
    public ResponseEntity<Void> linkMedia(@PathVariable UUID id, @RequestBody @Valid LinkMediaRequest request) {
        service.linkMedia(id, request);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/collections/{id}/tmdb-reference")
    public ResponseEntity<Void> setTmdbReference(@PathVariable UUID id,
                                                  @RequestBody @Valid SetTmdbCollectionReferenceRequest request) {
        service.setTmdbCollectionReference(id, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/collections/import")
    public ResponseEntity<CatalogOperationAcceptedResponse> importCollection(
            @RequestBody @Valid ImportTmdbCollectionRequest request) {
        CatalogOperationAcceptedResponse result = jobOrchestrationService.importCollection(
                request.provider(), request.externalId(), request.locale(), null);
        return ResponseEntity.accepted().body(result);
    }

    @PostMapping("/collections/{id}/sync")
    public ResponseEntity<CatalogOperationAcceptedResponse> syncCollection(
            @PathVariable UUID id, @RequestParam(defaultValue = "pt-BR") String locale) {
        CatalogOperationAcceptedResponse result = jobOrchestrationService.syncCollection(id, locale,
                "MANUAL", 100, null);
        return ResponseEntity.accepted().body(result);
    }

    @DeleteMapping("/collections/{id}/field-ownership/{field}")
    public ResponseEntity<CatalogOperationAcceptedResponse> resetCollectionFieldToSource(
            @PathVariable UUID id, @PathVariable String field,
            @RequestParam(defaultValue = "pt-BR") String locale) {
        service.resetCollectionFieldToSource(id, field);
        CatalogOperationAcceptedResponse result = jobOrchestrationService.syncCollection(id, locale,
                "MANUAL", 100, null);
        return ResponseEntity.accepted().body(result);
    }

    @GetMapping("/collections/{id}/audit")
    public CollectionAuditResponse collectionAudit(@PathVariable UUID id) {
        return collectionAuditService.audit(id);
    }

    @GetMapping("/collections/discovery-status")
    public TmdbCollectionDiscoveryService.DiscoveryStatus collectionDiscoveryStatus() {
        return collectionDiscoveryService.status();
    }

    @PostMapping("/collections/discover-from-catalog")
    public ResponseEntity<Void> discoverCollectionsFromCatalog() {
        return collectionDiscoveryService.startDiscovery()
                ? ResponseEntity.<Void>accepted().build()
                : ResponseEntity.<Void>status(HttpStatus.CONFLICT).build();
    }

    @PutMapping("/franchises/{id}/collections")
    public ResponseEntity<Void> linkCollection(@PathVariable UUID id,
                                                @RequestBody @Valid LinkCollectionRequest request) {
        service.linkCollection(id, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/collections/{id}")
    public ResponseEntity<Void> hideCollection(@PathVariable UUID id) {
        service.hideCollection(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/franchises/{id}")
    public ResponseEntity<Void> hideFranchise(@PathVariable UUID id) {
        service.hideFranchise(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/collections")
    public PageResponse<CollectionSummaryResponse> collections(@RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return service.collections(query, page, Math.min(size, 100));
    }

    @GetMapping("/franchises")
    public PageResponse<FranchiseSummaryResponse> franchises(@RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return service.franchises(query, page, Math.min(size, 100));
    }
}
