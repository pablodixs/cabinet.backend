package com.scriptles.cabinet.media.controller;

import com.scriptles.cabinet.media.dto.response.ReviewLikeResponse;
import com.scriptles.cabinet.media.dto.response.ReviewLikerResponse;
import com.scriptles.cabinet.media.service.ReviewLikeService;
import com.scriptles.cabinet.security.AuthenticatedUser;
import com.scriptles.cabinet.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReviewLikeController.class)
@Import(SecurityConfig.class)
class ReviewLikeControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReviewLikeService reviewLikeService;

    @Test
    void rejectsAnonymousReviewLikeLookup() throws Exception {
        mockMvc.perform(get("/v1/me/review-likes/{reviewId}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void likesReviewWithCsrf() throws Exception {
        AuthenticatedUser principal = principal();
        UUID reviewId = UUID.randomUUID();
        UUID likerId = UUID.randomUUID();
        when(reviewLikeService.like(principal.id(), reviewId))
                .thenReturn(new ReviewLikeResponse(
                        true,
                        9,
                        List.of(new ReviewLikerResponse(likerId, "ana", "https://example.com/ana.jpg"))
                ));

        mockMvc.perform(put("/v1/me/review-likes/{reviewId}", reviewId)
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(true))
                .andExpect(jsonPath("$.likeCount").value(9))
                .andExpect(jsonPath("$.recentLikers[0].id").value(likerId.toString()))
                .andExpect(jsonPath("$.recentLikers[0].username").value("ana"))
                .andExpect(jsonPath("$.recentLikers[0].avatarUrl")
                        .value("https://example.com/ana.jpg"));

        verify(reviewLikeService).like(principal.id(), reviewId);
    }

    @Test
    void unlikesReviewWithCsrf() throws Exception {
        AuthenticatedUser principal = principal();
        UUID reviewId = UUID.randomUUID();
        when(reviewLikeService.unlike(principal.id(), reviewId))
                .thenReturn(new ReviewLikeResponse(false, 8, List.of()));

        mockMvc.perform(delete("/v1/me/review-likes/{reviewId}", reviewId)
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(false))
                .andExpect(jsonPath("$.likeCount").value(8));

        verify(reviewLikeService).unlike(principal.id(), reviewId);
    }

    @Test
    void requiresCsrfForReviewLikeMutation() throws Exception {
        mockMvc.perform(put("/v1/me/review-likes/{reviewId}", UUID.randomUUID())
                        .with(user(principal())))
                .andExpect(status().isForbidden());
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
}
