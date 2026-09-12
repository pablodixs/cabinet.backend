package com.scriptles.cabinet.user.controller;

import com.scriptles.cabinet.security.AuthenticatedUser;
import com.scriptles.cabinet.user.dto.request.UpdateUserProfile;
import com.scriptles.cabinet.user.dto.response.UserProfileResponse;
import com.scriptles.cabinet.user.service.UserProfileService;
import com.scriptles.cabinet.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/me/profile")
@RequiredArgsConstructor
public class MeProfileController {
    private final UserService userService;
    private final UserProfileService userProfileService;

    @PatchMapping
    public UserProfileResponse update(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody @Valid UpdateUserProfile request
    ) {
        userService.updateProfile(user.id(), request);
        return userProfileService.findByUsername(user.username(), user.id());
    }
}
