package com.scriptles.cabinet.user.controller;

import com.scriptles.cabinet.security.AuthenticatedUser;
import com.scriptles.cabinet.user.dto.request.UpdateProfileFavoritesRequest;
import com.scriptles.cabinet.user.service.ProfileFavoriteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/me/profile/favorites")
@RequiredArgsConstructor
public class MeProfileFavoriteController {
    private final ProfileFavoriteService favoriteService;

    @PutMapping
    public ResponseEntity<Void> replace(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody @Valid UpdateProfileFavoritesRequest request
    ) {
        favoriteService.replace(user.id(), request.mediaIds());
        return ResponseEntity.noContent().build();
    }
}
