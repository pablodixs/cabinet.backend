package com.scriptles.cabinet.user.controller;

import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.security.AuthenticatedUser;
import com.scriptles.cabinet.user.dto.request.UpdateUserRoleRequest;
import com.scriptles.cabinet.user.dto.request.UpdateAccountTierRequest;
import com.scriptles.cabinet.user.dto.response.CommunityUserRoleResponse;
import com.scriptles.cabinet.user.service.CommunityRoleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@Validated
@RequestMapping("/v1/admin/community-users")
@PreAuthorize("@communityAuthorization.isAdmin(authentication)")
@RequiredArgsConstructor
public class CommunityRoleController {
    private final CommunityRoleService communityRoleService;

    @GetMapping
    public PageResponse<CommunityUserRoleResponse> findUsers(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size
    ) {
        return communityRoleService.findUsers(query, page, size);
    }

    @PatchMapping("/{userId}/role")
    public CommunityUserRoleResponse updateRole(
            @PathVariable UUID userId,
            @RequestBody @Valid UpdateUserRoleRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor
    ) {
        return communityRoleService.updateRole(userId, request.role(), actor.id());
    }

    @PatchMapping("/{userId}/tier")
    public CommunityUserRoleResponse updateAccountTier(
            @PathVariable UUID userId,
            @RequestBody @Valid UpdateAccountTierRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor
    ) {
        return communityRoleService.updateAccountTier(userId, request.accountTier(), actor.id());
    }
}
