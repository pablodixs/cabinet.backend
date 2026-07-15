package com.scriptles.cabinet.user.controller;

import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.user.dto.request.CreateUserRequest;
import com.scriptles.cabinet.user.dto.response.ProfileActivityResponse;
import com.scriptles.cabinet.user.dto.response.UserProfileResponse;
import com.scriptles.cabinet.security.AuthenticatedUser;
import com.scriptles.cabinet.user.service.UserProfileService;
import com.scriptles.cabinet.user.service.UserService;
import lombok.RequiredArgsConstructor;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.validation.annotation.Validated;

@RestController
@RequestMapping("/v1/users")
@RequiredArgsConstructor
@Validated
public class UserController {
    private final UserService userService;
    private final UserProfileService userProfileService;

    @GetMapping("/{username}/profile")
    public UserProfileResponse findProfile(
            @PathVariable String username,
            @AuthenticationPrincipal AuthenticatedUser viewer
    ) {
        return userProfileService.findByUsername(
                username,
                viewer == null ? null : viewer.id()
        );
    }

    @GetMapping("/{username}/activities")
    public PageResponse<ProfileActivityResponse> findActivities(
            @PathVariable String username,
            @AuthenticationPrincipal AuthenticatedUser viewer,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size
    ) {
        return userProfileService.findActivities(
                username,
                viewer == null ? null : viewer.id(),
                page,
                size
        );
    }

    @PostMapping("/create")
    public ResponseEntity<Void> createUser(@RequestBody CreateUserRequest request) {
        userService.createUser(request);

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
