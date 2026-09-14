package com.scriptles.cabinet.profile.controller;
import com.scriptles.cabinet.common.api.PageResponse; import com.scriptles.cabinet.profile.dto.*; import com.scriptles.cabinet.profile.service.*; import com.scriptles.cabinet.security.AuthenticatedUser; import jakarta.validation.Valid; import jakarta.validation.constraints.*; import lombok.RequiredArgsConstructor; import org.springframework.http.ResponseEntity; import org.springframework.security.core.annotation.AuthenticationPrincipal; import org.springframework.validation.annotation.Validated; import org.springframework.web.bind.annotation.*; import java.util.UUID;
@RestController @RequestMapping("/v1/profiles") @RequiredArgsConstructor @Validated
public class ProfileController { private final ProfileService profiles; private final HQClaimService claims;
 @GetMapping("/{handle}") public ProfileResponse find(@PathVariable String handle,@AuthenticationPrincipal AuthenticatedUser viewer){return profiles.find(handle,viewer==null?null:viewer.id());}
 @GetMapping("/search") public PageResponse<ProfileSearchResponse> search(@RequestParam @Size(min=2,max=80) String query,@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(50) int size){return profiles.search(query,page,size);}
 @GetMapping("/{handle}/posts") public PageResponse<PostResponse> posts(@PathVariable String handle,@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(50) int size){return postService.list(handle,page,size);}
 @GetMapping("/{handle}/catalog") public PageResponse<CatalogItemResponse> catalog(@PathVariable String handle,@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(50) int size){return profiles.catalog(handle,page,size);}
 @PostMapping("/{profileId}/claims") public ClaimResponse claim(@AuthenticationPrincipal AuthenticatedUser viewer,@PathVariable UUID profileId,@Valid @RequestBody ClaimRequest request){return claims.submit(viewer.id(),profileId,request);}
 private final PostService postService;
}
