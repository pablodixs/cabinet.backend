package com.scriptles.cabinet.notifications.controller;

import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.notifications.dto.MarkNotificationsReadRequest;
import com.scriptles.cabinet.notifications.dto.NotificationResponse;
import com.scriptles.cabinet.notifications.dto.UnreadCountResponse;
import com.scriptles.cabinet.notifications.service.NotificationService;
import com.scriptles.cabinet.notifications.service.NotificationStreamService;
import com.scriptles.cabinet.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/v1/me/notifications")
@Validated
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notificationService;
    private final NotificationStreamService streamService;

    @GetMapping
    public PageResponse<NotificationResponse> find(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return notificationService.find(user.id(), page, size);
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse unreadCount(@AuthenticationPrincipal AuthenticatedUser user) {
        return notificationService.unreadCount(user.id());
    }

    @PatchMapping("/read")
    public ResponseEntity<Void> markRead(@AuthenticationPrincipal AuthenticatedUser user,
                                         @RequestBody @Valid MarkNotificationsReadRequest request) {
        notificationService.markRead(user.id(), request.ids());
        return ResponseEntity.noContent().build();
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal AuthenticatedUser user) {
        return streamService.subscribe(user.id());
    }
}
