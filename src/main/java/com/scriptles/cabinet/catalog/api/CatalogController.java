package com.scriptles.cabinet.catalog.api;
import com.scriptles.cabinet.catalog.service.CatalogQueryService; import com.scriptles.cabinet.security.AuthenticatedUser; import lombok.RequiredArgsConstructor; import org.springframework.security.core.annotation.AuthenticationPrincipal; import org.springframework.web.bind.annotation.*; import java.util.UUID;
@RestController @RequestMapping("/v1") @RequiredArgsConstructor public class CatalogController { private final CatalogQueryService service;
 @GetMapping("/collections/{id}") public CollectionResponse collection(@PathVariable UUID id,@AuthenticationPrincipal AuthenticatedUser user){return service.collection(id,user==null?null:user.id());}
 @GetMapping("/collections/slug/{slug}") public CollectionResponse collectionSlug(@PathVariable String slug,@AuthenticationPrincipal AuthenticatedUser user){return service.collectionBySlug(slug,user==null?null:user.id());}
 @GetMapping("/franchises/{id}") public FranchiseResponse franchise(@PathVariable UUID id,@AuthenticationPrincipal AuthenticatedUser user){return service.franchise(id,user==null?null:user.id());}
 @GetMapping("/franchises/slug/{slug}") public FranchiseResponse franchiseSlug(@PathVariable String slug,@AuthenticationPrincipal AuthenticatedUser user){return service.franchiseBySlug(slug,user==null?null:user.id());}
}
