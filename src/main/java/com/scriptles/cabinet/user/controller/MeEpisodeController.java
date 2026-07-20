package com.scriptles.cabinet.user.controller;

import com.scriptles.cabinet.security.AuthenticatedUser;
import com.scriptles.cabinet.user.dto.request.MarkEpisodeWatchedRequest;
import com.scriptles.cabinet.user.dto.response.EpisodeAgendaResponse;
import com.scriptles.cabinet.user.dto.response.EpisodeWatchResponse;
import com.scriptles.cabinet.user.service.EpisodeAgendaService;
import com.scriptles.cabinet.user.service.EpisodeTrackingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/me/episodes")
@RequiredArgsConstructor
@Validated
public class MeEpisodeController {
    private final EpisodeAgendaService agendaService;
    private final EpisodeTrackingService trackingService;

    @GetMapping("/agenda")
    public EpisodeAgendaResponse agenda(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "30") @Min(1) @Max(90) int days,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int overdueLimit
    ) {
        return agendaService.find(user.id(), days, overdueLimit);
    }

    @PutMapping("/{episodeMediaId}/watched")
    public EpisodeWatchResponse markWatched(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID episodeMediaId,
            @RequestBody @Valid MarkEpisodeWatchedRequest request
    ) {
        return trackingService.markWatched(user.id(), episodeMediaId, request.includePrevious());
    }

    @DeleteMapping("/{episodeMediaId}/watched")
    public EpisodeWatchResponse unmarkWatched(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID episodeMediaId
    ) {
        return trackingService.unmarkWatched(user.id(), episodeMediaId);
    }
}
