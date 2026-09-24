package com.scriptles.cabinet.user.controller;

import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.common.api.CursorPageResponse;
import com.scriptles.cabinet.user.dto.request.CreateUserRequest;
import com.scriptles.cabinet.user.dto.response.ProfileActivityResponse;
import com.scriptles.cabinet.user.dto.response.UserSearchResponse;
import com.scriptles.cabinet.user.dto.response.UserProfileResponse;
import com.scriptles.cabinet.user.dto.response.UserSummaryResponse;
import com.scriptles.cabinet.user.dto.response.SocialUserResponse;
import com.scriptles.cabinet.user.dto.response.LibraryMediaResponse;
import com.scriptles.cabinet.user.dto.response.LibraryFilterOptionsResponse;
import com.scriptles.cabinet.user.dto.response.ProfileLikeResponse;
import com.scriptles.cabinet.user.dto.response.ProfileTagResponse;
import com.scriptles.cabinet.user.dto.response.ProfileTaggedMediaResponse;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.translation.CatalogLocaleResolver;
import com.scriptles.cabinet.user.enums.UserMediaStatus;
import com.scriptles.cabinet.user.enums.LibraryRatingFilter;
import com.scriptles.cabinet.user.enums.LibrarySort;
import com.scriptles.cabinet.security.AuthenticatedUser;
import com.scriptles.cabinet.user.service.UserProfileService;
import com.scriptles.cabinet.user.service.UserService;
import com.scriptles.cabinet.user.service.SocialGraphService;
import lombok.RequiredArgsConstructor;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@RestController
@RequestMapping("/v1/users")
@RequiredArgsConstructor
@Validated
public class UserController {
    private final UserService userService;
    private final UserProfileService userProfileService;
    private static final CatalogLocaleResolver localeResolver = new CatalogLocaleResolver();
    private final SocialGraphService socialGraphService;

    @GetMapping("/search")
    public PageResponse<UserSearchResponse> search(
            @AuthenticationPrincipal AuthenticatedUser viewer,
            @RequestParam @Size(min = 3, max = 80) String query,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size
    ) {
        return viewer == null
                ? userProfileService.search(query, page, size)
                : userProfileService.search(query, viewer.id(), page, size);
    }

    @GetMapping("/{username}/summary")
    public UserSummaryResponse findSummary(
            @PathVariable String username,
            @AuthenticationPrincipal AuthenticatedUser viewer
    ) {
        return userProfileService.findSummary(username, viewer.id());
    }

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

    @GetMapping("/{username}/followers")
    public CursorPageResponse<SocialUserResponse> followers(
            @PathVariable String username,
            @AuthenticationPrincipal AuthenticatedUser viewer,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size
    ) {
        return socialGraphService.followers(
                username, viewer == null ? null : viewer.id(), cursor, size);
    }

    @GetMapping("/{username}/following")
    public CursorPageResponse<SocialUserResponse> following(
            @PathVariable String username,
            @AuthenticationPrincipal AuthenticatedUser viewer,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size
    ) {
        return socialGraphService.following(
                username, viewer == null ? null : viewer.id(), cursor, size);
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

    @GetMapping("/{username}/activities/{mediaId}")
    public List<ProfileActivityResponse> findActivitiesByMedia(
            @PathVariable String username,
            @PathVariable java.util.UUID mediaId,
            @AuthenticationPrincipal AuthenticatedUser viewer
    ) {
        return userProfileService.findActivitiesByMedia(
                username,
                mediaId,
                viewer == null ? null : viewer.id()
        );
    }

    @GetMapping("/{username}/library")
    public PageResponse<LibraryMediaResponse> findLibrary(
            @PathVariable String username,
            @AuthenticationPrincipal AuthenticatedUser viewer,
            @RequestParam(required = false) UserMediaStatus status,
            @RequestParam(required = false) MediaType type,
            @RequestParam(required = false) @Size(max = 100) String query,
            @RequestParam(required = false) @Size(max = 100) String genre,
            @RequestParam(defaultValue = "ALL") LibraryRatingFilter rating,
            @RequestParam(defaultValue = "RECENT") LibrarySort sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size
    ) {
        return userProfileService.findLibrary(
                username,
                viewer == null ? null : viewer.id(),
                status,
                type,
                query,
                genre,
                rating,
                sort,
                page,
                size
        );
    }

    @GetMapping("/{username}/library/filters")
    public LibraryFilterOptionsResponse findLibraryFilters(
            @PathVariable String username,
            @AuthenticationPrincipal AuthenticatedUser viewer,
            @RequestParam(required = false) String locale,
            @RequestHeader(name = "Accept-Language", required = false) String acceptLanguage
    ) {
        return userProfileService.findLibraryFilters(
                username, viewer == null ? null : viewer.id(),
                localeResolver.resolve(locale, acceptLanguage).tag());
    }

    @GetMapping("/{username}/likes")
    public PageResponse<ProfileLikeResponse> findLikes(
            @PathVariable String username,
            @AuthenticationPrincipal AuthenticatedUser viewer,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "30") @Min(1) @Max(50) int size
    ) {
        return userProfileService.findLikes(
                username, viewer == null ? null : viewer.id(), page, size);
    }

    @GetMapping("/{username}/tags")
    public List<ProfileTagResponse> findTags(
            @PathVariable String username,
            @AuthenticationPrincipal AuthenticatedUser viewer,
            @RequestParam(defaultValue = "100") @Min(1) @Max(200) int limit
    ) {
        return userProfileService.findTags(
                username, viewer == null ? null : viewer.id(), limit);
    }

    @GetMapping("/{username}/tagged-media")
    public List<ProfileTaggedMediaResponse> findTaggedMedia(
            @PathVariable String username,
            @AuthenticationPrincipal AuthenticatedUser viewer,
            @RequestParam @Size(min = 1, max = 100) String tag
    ) {
        return userProfileService.findTaggedMedia(
                username, viewer == null ? null : viewer.id(), tag);
    }

    @PostMapping("/create")
    public ResponseEntity<Void> createUser(@RequestBody CreateUserRequest request) {
        userService.createUser(request);

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
