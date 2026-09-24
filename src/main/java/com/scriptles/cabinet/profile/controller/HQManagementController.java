package com.scriptles.cabinet.profile.controller;
import com.scriptles.cabinet.profile.dto.*;
import com.scriptles.cabinet.profile.service.ProfileService;
import com.scriptles.cabinet.profile.dto.HQUpdateRequest;
import com.scriptles.cabinet.profile.dto.ProfileResponse;
import com.scriptles.cabinet.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/v1/hq/{profileId}") @RequiredArgsConstructor
public class HQManagementController {
    private final ProfileService profiles;
    @PutMapping public ProfileResponse update(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID profileId, @Valid @RequestBody HQUpdateRequest request) { return profiles.updateHQ(profileId, request, actor.id()); }
}
