package com.scriptles.cabinet.user.controller;

import com.scriptles.cabinet.security.AuthenticatedUser;
import com.scriptles.cabinet.user.dto.request.ReorderUserAlbumRotationRequest;
import com.scriptles.cabinet.user.dto.response.UserAlbumRotationResponse;
import com.scriptles.cabinet.user.service.UserAlbumRotationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class UserAlbumRotationController {
    private final UserAlbumRotationService rotationService;

    @GetMapping("/v1/me/music/rotation")
    public List<UserAlbumRotationResponse> mine(@AuthenticationPrincipal AuthenticatedUser user) {
        return rotationService.findMine(user.id());
    }

    @PutMapping("/v1/me/music/rotation/{albumId}")
    public UserAlbumRotationResponse add(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID albumId
    ) {
        return rotationService.add(user.id(), albumId);
    }

    @DeleteMapping("/v1/me/music/rotation/{albumId}")
    public ResponseEntity<Void> remove(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID albumId
    ) {
        rotationService.remove(user.id(), albumId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/v1/me/music/rotation/order")
    public List<UserAlbumRotationResponse> reorder(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody @Valid ReorderUserAlbumRotationRequest request
    ) {
        return rotationService.reorder(user.id(), request);
    }

    @GetMapping("/v1/users/{username}/music/rotation")
    public List<UserAlbumRotationResponse> publicRotation(
            @PathVariable String username,
            @AuthenticationPrincipal AuthenticatedUser viewer
    ) {
        return rotationService.findByUsername(username, viewer == null ? null : viewer.id());
    }
}
