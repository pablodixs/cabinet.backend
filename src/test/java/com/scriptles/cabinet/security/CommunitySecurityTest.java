package com.scriptles.cabinet.security;

import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.media.controller.ReviewController;
import com.scriptles.cabinet.media.controller.RatingController;
import com.scriptles.cabinet.media.controller.MediaLikeController;
import com.scriptles.cabinet.media.dto.request.UpsertReviewRequest;
import com.scriptles.cabinet.media.dto.response.ReviewResponse;
import com.scriptles.cabinet.media.dto.response.RatingResponse;
import com.scriptles.cabinet.media.service.ReviewService;
import com.scriptles.cabinet.media.service.RatingService;
import com.scriptles.cabinet.media.dto.response.MediaLikeResponse;
import com.scriptles.cabinet.media.service.MediaLikeService;
import com.scriptles.cabinet.user.controller.MeLibraryController;
import com.scriptles.cabinet.user.enums.Visibility;
import com.scriptles.cabinet.user.service.UserMediaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({ReviewController.class, RatingController.class, MediaLikeController.class, MeLibraryController.class})
@Import(SecurityConfig.class)
class CommunitySecurityTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReviewService reviewService;

    @MockitoBean
    private RatingService ratingService;

    @MockitoBean
    private MediaLikeService mediaLikeService;

    @MockitoBean
    private UserMediaService userMediaService;

    @Test
    void rejectsAnonymousLikeLookup() throws Exception {
        mockMvc.perform(get("/v1/me/likes/{mediaId}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void acceptsAuthenticatedLikeMutationWithCsrf() throws Exception {
        AuthenticatedUser principal = principal();
        UUID mediaId = UUID.randomUUID();
        when(mediaLikeService.like(principal.id(), mediaId))
                .thenReturn(new MediaLikeResponse(true));

        mockMvc.perform(put("/v1/me/likes/{mediaId}", mediaId)
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(true));

        verify(mediaLikeService).like(principal.id(), mediaId);
    }

    @Test
    void requiresCsrfForAuthenticatedLikeMutation() throws Exception {
        mockMvc.perform(put("/v1/me/likes/{mediaId}", UUID.randomUUID())
                        .with(user(principal())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void allowsAnonymousUsersToReadPublicReviews() throws Exception {
        UUID mediaId = UUID.randomUUID();
        when(reviewService.findPublic(null, mediaId, 0, 10))
                .thenReturn(new PageResponse<>(List.of(), 0, 10, 0, 0));

        mockMvc.perform(get("/v1/media/{mediaId}/reviews", mediaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    void allowsAnonymousUsersToReadPopularAndRecentReviews() throws Exception {
        UUID mediaId = UUID.randomUUID();
        when(reviewService.findPopular(null, mediaId)).thenReturn(List.of());
        when(reviewService.findRecent(null, mediaId)).thenReturn(List.of());

        mockMvc.perform(get("/v1/media/{mediaId}/reviews/popular", mediaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        mockMvc.perform(get("/v1/media/{mediaId}/reviews/recent", mediaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void rejectsAnonymousPersonalRequestsWithJsonError() throws Exception {
        mockMvc.perform(get("/v1/me/reviews/{mediaId}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void returnsTheAuthenticatedUsersRating() throws Exception {
        AuthenticatedUser principal = principal();
        UUID mediaId = UUID.randomUUID();
        when(ratingService.find(principal.id(), mediaId))
                .thenReturn(Optional.of(new RatingResponse(mediaId, new BigDecimal("4.5"))));

        mockMvc.perform(get("/v1/me/ratings/{mediaId}", mediaId)
                        .with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(4.5));
    }

    @Test
    void requiresAuthenticationForExternalImport() throws Exception {
        mockMvc.perform(post("/v1/media/external/import")
                        .with(csrf())
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void requiresCsrfForAuthenticatedReviewMutation() throws Exception {
        AuthenticatedUser principal = principal();

        mockMvc.perform(put("/v1/me/reviews/{mediaId}", UUID.randomUUID())
                        .with(user(principal))
                        .contentType("application/json")
                        .content(reviewBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void acceptsAuthenticatedReviewMutationWithCsrf() throws Exception {
        AuthenticatedUser principal = principal();
        UUID mediaId = UUID.randomUUID();
        ReviewResponse response = new ReviewResponse(
                UUID.randomUUID(),
                mediaId,
                new BigDecimal("4.5"),
                "Ótimo",
                false,
                Visibility.PUBLIC,
                Instant.parse("2026-07-14T12:00:00Z"),
                Instant.parse("2026-07-14T12:00:00Z"),
                0,
                false,
                List.of(),
                new ReviewResponse.AuthorResponse(
                        principal.id(),
                        "maria",
                        "Maria",
                        null
                )
        );
        when(reviewService.upsert(eq(principal.id()), eq(mediaId), any(UpsertReviewRequest.class)))
                .thenReturn(response);

        mockMvc.perform(put("/v1/me/reviews/{mediaId}", mediaId)
                        .with(user(principal))
                        .with(csrf())
                        .contentType("application/json")
                        .content(reviewBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(4.5));

        verify(reviewService).upsert(eq(principal.id()), eq(mediaId), any(UpsertReviewRequest.class));
    }

    @Test
    void acceptsIdempotentReviewDeletionWithCsrf() throws Exception {
        AuthenticatedUser principal = principal();
        UUID mediaId = UUID.randomUUID();

        mockMvc.perform(delete("/v1/me/reviews/{mediaId}", mediaId)
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(reviewService).delete(principal.id(), mediaId);
    }

    @Test
    void returnsNoContentWhenPersonalLibraryEntryDoesNotExist() throws Exception {
        AuthenticatedUser principal = principal();
        UUID mediaId = UUID.randomUUID();
        when(userMediaService.find(principal.id(), mediaId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/v1/me/library/{mediaId}", mediaId)
                        .with(user(principal)))
                .andExpect(status().isNoContent());
    }

    @Test
    void listsOnlyTheAuthenticatedUsersLibrary() throws Exception {
        AuthenticatedUser principal = principal();
        when(userMediaService.findLibrary(principal.id(), null, null, 0, 20))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/v1/me/library")
                        .with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.totalElements").value(0));

        verify(userMediaService).findLibrary(principal.id(), null, null, 0, 20);
    }

    @Test
    void rejectsAnonymousLibraryListing() throws Exception {
        mockMvc.perform(get("/v1/me/library"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    private AuthenticatedUser principal() {
        return new AuthenticatedUser(
                UUID.randomUUID(),
                "maria@example.com",
                "maria",
                "Maria",
                "encoded-password",
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                true
        );
    }

    private String reviewBody() {
        return """
                {
                  "rating": 4.5,
                  "content": "Ótimo",
                  "containsSpoilers": false,
                  "visibility": "PUBLIC"
                }
                """;
    }
}
