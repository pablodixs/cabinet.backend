package com.scriptles.cabinet.profile.controller;
import com.scriptles.cabinet.profile.dto.*;
import com.scriptles.cabinet.profile.service.HQManagementService;
import com.scriptles.cabinet.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/v1/hq/{profileId}") @RequiredArgsConstructor
public class HQManagementController {
    private final HQManagementService service;
    @GetMapping("/members") public List<HQMemberResponse> list(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID profileId) { return service.list(actor.id(), profileId); }
    @PostMapping("/members") public HQMemberResponse add(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID profileId, @Valid @RequestBody HQMemberRequest request) { return service.add(actor.id(), profileId, request); }
    @PatchMapping("/members/{memberId}") public HQMemberResponse change(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID profileId, @PathVariable UUID memberId, @Valid @RequestBody HQMemberRequest request) { return service.changeRole(actor.id(), profileId, memberId, request); }
    @DeleteMapping("/members/{memberId}") public void remove(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID profileId, @PathVariable UUID memberId) { service.remove(actor.id(), profileId, memberId); }
}
