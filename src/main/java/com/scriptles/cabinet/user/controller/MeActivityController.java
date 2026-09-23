package com.scriptles.cabinet.user.controller;

import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.security.AuthenticatedUser;
import com.scriptles.cabinet.user.dto.response.FeedActivityResponse;
import com.scriptles.cabinet.user.service.UserFeedService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/me/activity")
@RequiredArgsConstructor
@Validated
public class MeActivityController {
    private final UserFeedService userFeedService;

    @GetMapping
    public PageResponse<FeedActivityResponse> find(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "FRIENDS") String feed,
            @RequestParam(defaultValue = "false") boolean interactionsOnly,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        boolean friendsOnly = switch (feed.toUpperCase()) {
            case "FRIENDS" -> true;
            case "YOU" -> false;
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ACTIVITY_FEED",
                    "Feed must be FRIENDS or YOU");
        };
        return userFeedService.find(user.id(), friendsOnly, interactionsOnly, page, size);
    }
}
