package com.scriptles.cabinet.media.controller;

import com.scriptles.cabinet.media.dto.response.ReviewLikeResponse;
import com.scriptles.cabinet.media.service.ReviewLikeService;
import com.scriptles.cabinet.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/me/review-likes")
@RequiredArgsConstructor
public class ReviewLikeController {
    private final ReviewLikeService reviewLikeService;

    @GetMapping("/{reviewId}")
    public ReviewLikeResponse find(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID reviewId
    ) {
        return reviewLikeService.find(user.id(), reviewId);
    }

    @PutMapping("/{reviewId}")
    public ReviewLikeResponse like(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID reviewId
    ) {
        return reviewLikeService.like(user.id(), reviewId);
    }

    @DeleteMapping("/{reviewId}")
    public ReviewLikeResponse unlike(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID reviewId
    ) {
        return reviewLikeService.unlike(user.id(), reviewId);
    }
}
