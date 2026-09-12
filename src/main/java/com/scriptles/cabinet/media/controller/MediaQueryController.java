package com.scriptles.cabinet.media.controller;

import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.media.dto.response.AlbumTracksResponse;
import com.scriptles.cabinet.media.dto.response.ExternalMediaDetailsResponse;
import com.scriptles.cabinet.media.dto.response.MediaCommunityResponse;
import com.scriptles.cabinet.media.dto.response.PublicMediaDetailsResponse;
import com.scriptles.cabinet.media.dto.response.UserMediaStateResponse;
import com.scriptles.cabinet.media.dto.response.MediaExternalInfoResponse;
import com.scriptles.cabinet.media.dto.response.AwardPageResponse;
import com.scriptles.cabinet.media.dto.response.MoreByResponse;
import com.scriptles.cabinet.media.enums.CreditRole;
import com.scriptles.cabinet.media.enums.AwardResult;
import com.scriptles.cabinet.media.enums.CatalogStatus;
import com.scriptles.cabinet.media.service.MediaExternalInfoService;
import com.scriptles.cabinet.media.service.MediaQueryService;
import com.scriptles.cabinet.media.service.MoreByService;
import com.scriptles.cabinet.media.service.AwardQueryService;
import com.scriptles.cabinet.media.service.SeasonEpisodeService;
import com.scriptles.cabinet.media.dto.response.SeasonEpisodesResponse;
import com.scriptles.cabinet.media.translation.CatalogLocaleResolver;
import com.scriptles.cabinet.security.AuthenticatedUser;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.UUID;

@RestController
@Validated
@RequestMapping("/v1/media")
@RequiredArgsConstructor
public class MediaQueryController {
    private final MediaQueryService mediaQueryService;
    private final MediaExternalInfoService mediaExternalInfoService;
    private final MoreByService moreByService;
    private final SeasonEpisodeService seasonEpisodeService;
    private final AwardQueryService awardQueryService;
    private final CatalogLocaleResolver catalogLocaleResolver;

    @GetMapping("/{mediaId}")
    public ResponseEntity<PublicMediaDetailsResponse> findDetails(
            @PathVariable UUID mediaId,
            @RequestParam(required = false) String locale,
            @RequestHeader(name = "Accept-Language", required = false) String acceptLanguage
    ) {
        String requestedLocale = catalogLocaleResolver.resolve(locale, acceptLanguage).tag();
        PublicMediaDetailsResponse response = mediaQueryService.findDetails(mediaId, requestedLocale);
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .header("Content-Language", response.resolvedLocale());
        if (response.catalogStatus() == CatalogStatus.READY) {
            builder.header("Cache-Control",
                    "public, max-age=60, s-maxage=3600, stale-while-revalidate=86400");
        } else {
            builder.header("Cache-Control", "no-store, max-age=0")
                    .header("Retry-After", "2");
        }
        if (locale == null || locale.isBlank()) {
            builder.header("Vary", "Accept-Language");
        }
        return builder.body(response);
    }

    @GetMapping("/{mediaId}/community")
    public MediaCommunityResponse findCommunity(@PathVariable UUID mediaId) {
        return mediaQueryService.findCommunity(mediaId);
    }

    @GetMapping("/{albumId}/tracks")
    public ResponseEntity<AlbumTracksResponse> findAlbumTracks(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID albumId
    ) {
        return ResponseEntity.ok()
                .header("Cache-Control", "private, no-store")
                .body(mediaQueryService.findAlbumTracks(albumId, user == null ? null : user.id()));
    }

    @GetMapping("/{mediaId}/me")
    public UserMediaStateResponse findUserState(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID mediaId
    ) {
        return mediaQueryService.findUserState(mediaId, user.id());
    }

    @GetMapping("/{mediaId}/credits")
    public PageResponse<ExternalMediaDetailsResponse.CreditResponse> findCredits(
            @PathVariable UUID mediaId,
            @RequestParam CreditRole role,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(40) int limit
    ) {
        return mediaQueryService.findCredits(mediaId, role, page, limit);
    }

    @GetMapping("/{mediaId}/more-by")
    public MoreByResponse findMoreBy(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID mediaId,
            @RequestParam(defaultValue = "pt-BR")
            @Pattern(regexp = "^[a-z]{2}(-[A-Z]{2})?$") String language,
            @RequestParam(defaultValue = "12") @Min(1) @Max(40) int limit
    ) {
        return user == null
                ? moreByService.find(mediaId, language, limit)
                : moreByService.find(mediaId, language, limit, user.id());
    }

    @GetMapping("/{mediaId}/external-info")
    public ResponseEntity<MediaExternalInfoResponse> findExternalInfo(
            @PathVariable UUID mediaId,
            @RequestParam(defaultValue = "BR")
            @Pattern(regexp = "^[A-Za-z]{2}$") String country
    ) {
        MediaExternalInfoResponse response = mediaExternalInfoService.find(mediaId, country);
        if (response.pendingWithoutData()) {
            return ResponseEntity.accepted()
                    .header("Retry-After", "2")
                    .body(response);
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{mediaId}/awards")
    public ResponseEntity<AwardPageResponse> findAwards(
            @PathVariable UUID mediaId,
            @RequestParam(required = false) AwardResult result,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        AwardPageResponse response = awardQueryService.findMedia(mediaId, result, page, size);
        return response.pendingWithoutData()
                ? ResponseEntity.accepted().header("Retry-After", "2").body(response)
                : ResponseEntity.ok(response);
    }

    @GetMapping("/{seriesId}/seasons/{seasonNumber}/episodes")
    public SeasonEpisodesResponse findSeasonEpisodes(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID seriesId,
            @PathVariable @Min(0) int seasonNumber,
            @RequestParam(defaultValue = "pt-BR")
            @Pattern(regexp = "^[a-z]{2}(-[A-Z]{2})?$") String language
    ) {
        return seasonEpisodeService.find(seriesId, seasonNumber, language, user == null ? null : user.id());
    }
}
