package com.scriptles.cabinet.catalog.api;
import com.scriptles.cabinet.catalog.entity.*; import com.scriptles.cabinet.catalog.service.CatalogModerationService; import jakarta.validation.Valid; import lombok.RequiredArgsConstructor; import org.springframework.http.*; import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.web.bind.annotation.*; import java.net.URI; import java.util.UUID;
@RestController @RequestMapping("/v1/moderation") @PreAuthorize("@communityAuthorization.isModerator(authentication)") @RequiredArgsConstructor public class CatalogModerationController { private final CatalogModerationService service;
 @PostMapping("/collections") public ResponseEntity<Void> createCollection(@RequestBody @Valid UpsertCollectionRequest r){Collection c=service.saveCollection(null,r);return ResponseEntity.created(URI.create("/v1/collections/"+c.getId())).build();}
 @PutMapping("/collections/{id}") public void updateCollection(@PathVariable UUID id,@RequestBody @Valid UpsertCollectionRequest r){service.saveCollection(id,r);}
 @PostMapping("/franchises") public ResponseEntity<Void> createFranchise(@RequestBody @Valid UpsertFranchiseRequest r){Franchise f=service.saveFranchise(null,r);return ResponseEntity.created(URI.create("/v1/franchises/"+f.getId())).build();}
 @PutMapping("/franchises/{id}") public void updateFranchise(@PathVariable UUID id,@RequestBody @Valid UpsertFranchiseRequest r){service.saveFranchise(id,r);}
}
