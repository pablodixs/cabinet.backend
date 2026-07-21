package com.scriptles.cabinet.user.controller;

import com.scriptles.cabinet.common.api.CursorPageResponse;
import com.scriptles.cabinet.security.AuthenticatedUser;
import com.scriptles.cabinet.user.dto.response.BlockedUserResponse;
import com.scriptles.cabinet.user.dto.response.FollowActionResponse;
import com.scriptles.cabinet.user.dto.response.SocialUserResponse;
import com.scriptles.cabinet.user.service.SocialGraphService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/me")
@RequiredArgsConstructor
@Validated
public class SocialGraphController {
    private final SocialGraphService socialGraphService;

    @PutMapping("/following/{targetUserId}")
    public FollowActionResponse follow(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID targetUserId) {
        return socialGraphService.follow(user.id(), targetUserId);
    }

    @DeleteMapping("/following/{targetUserId}")
    public ResponseEntity<Void> unfollow(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID targetUserId) {
        socialGraphService.unfollow(user.id(), targetUserId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/follow-requests/incoming")
    public CursorPageResponse<SocialUserResponse> incomingRequests(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return socialGraphService.incomingRequests(user.id(), cursor, size);
    }

    @GetMapping("/follow-requests/outgoing")
    public CursorPageResponse<SocialUserResponse> outgoingRequests(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return socialGraphService.outgoingRequests(user.id(), cursor, size);
    }

    @PutMapping("/follow-requests/{requesterId}/accept")
    public FollowActionResponse accept(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID requesterId) {
        return socialGraphService.accept(user.id(), requesterId);
    }

    @DeleteMapping("/follow-requests/{requesterId}")
    public ResponseEntity<Void> reject(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID requesterId) {
        socialGraphService.reject(user.id(), requesterId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/followers/{followerId}")
    public ResponseEntity<Void> removeFollower(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID followerId) {
        socialGraphService.removeFollower(user.id(), followerId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/blocks")
    public CursorPageResponse<BlockedUserResponse> blockedUsers(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return socialGraphService.blockedUsers(user.id(), cursor, size);
    }

    @PutMapping("/blocks/{targetUserId}")
    public ResponseEntity<Void> block(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID targetUserId) {
        socialGraphService.block(user.id(), targetUserId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/blocks/{targetUserId}")
    public ResponseEntity<Void> unblock(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID targetUserId) {
        socialGraphService.unblock(user.id(), targetUserId);
        return ResponseEntity.noContent().build();
    }
}
