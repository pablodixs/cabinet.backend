package com.scriptles.cabinet.notifications.controller;

import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.notifications.dto.MarkNotificationsReadRequest;
import com.scriptles.cabinet.notifications.dto.NotificationResponse;
import com.scriptles.cabinet.notifications.dto.UnreadCountResponse;
import com.scriptles.cabinet.notifications.dto.PushInstallationRequest;
import com.scriptles.cabinet.notifications.dto.PushPreferenceRequest;
import com.scriptles.cabinet.notifications.dto.PushPreferenceResponse;
import com.scriptles.cabinet.notifications.service.PushSettingsService;
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
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/me/notifications")
@Validated
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notificationService;
    private final NotificationStreamService streamService;
    private final PushSettingsService pushSettingsService;

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

    @GetMapping("/{notificationId}")
    public NotificationResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID notificationId) {
        return notificationService.get(user.id(), notificationId);
    }

    @PostMapping("/installations")
    public ResponseEntity<Void> register(@AuthenticationPrincipal AuthenticatedUser user,
                                         @RequestBody @Valid PushInstallationRequest request) {
        pushSettingsService.register(user.id(), request.token());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/installations")
    public ResponseEntity<Void> unregister(@AuthenticationPrincipal AuthenticatedUser user,
                                           @RequestBody @Valid PushInstallationRequest request) {
        pushSettingsService.unregister(user.id(), request.token());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/preferences")
    public List<PushPreferenceResponse> preferences(@AuthenticationPrincipal AuthenticatedUser user) {
        return pushSettingsService.findPreferences(user.id());
    }

    @PutMapping("/preferences")
    public List<PushPreferenceResponse> updatePreferences(@AuthenticationPrincipal AuthenticatedUser user,
                                                           @RequestBody @Valid PushPreferenceRequest request) {
        return pushSettingsService.updatePreferences(user.id(), request);
    }

    @PutMapping("/local-release-reminders/{mediaId}")
    public ResponseEntity<Void> registerLocalReleaseReminder(@AuthenticationPrincipal AuthenticatedUser user,
                                                               @PathVariable UUID mediaId) {
        notificationService.setLocalReleaseReminder(user.id(), mediaId, true);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/local-release-reminders/{mediaId}")
    public ResponseEntity<Void> unregisterLocalReleaseReminder(@AuthenticationPrincipal AuthenticatedUser user,
                                                                 @PathVariable UUID mediaId) {
        notificationService.setLocalReleaseReminder(user.id(), mediaId, false);
        return ResponseEntity.noContent().build();
    }
}
