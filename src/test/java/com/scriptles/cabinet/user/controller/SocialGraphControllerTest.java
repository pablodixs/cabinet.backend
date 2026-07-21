package com.scriptles.cabinet.user.controller;

import com.scriptles.cabinet.security.AuthenticatedUser;
import com.scriptles.cabinet.security.SecurityConfig;
import com.scriptles.cabinet.user.dto.response.FollowActionResponse;
import com.scriptles.cabinet.user.enums.FollowState;
import com.scriptles.cabinet.user.service.SocialGraphService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SocialGraphController.class)
@Import(SecurityConfig.class)
class SocialGraphControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean SocialGraphService socialGraphService;

    @Test
    void rejectsAnonymousFollowMutation() throws Exception {
        mockMvc.perform(put("/v1/me/following/{targetUserId}", UUID.randomUUID())
                        .with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void followsWithAuthenticatedPrincipalAndCsrf() throws Exception {
        AuthenticatedUser principal = principal();
        UUID targetId = UUID.randomUUID();
        when(socialGraphService.follow(principal.id(), targetId))
                .thenReturn(new FollowActionResponse(targetId, FollowState.FOLLOWING));

        mockMvc.perform(put("/v1/me/following/{targetUserId}", targetId)
                        .with(user(principal)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetUserId").value(targetId.toString()))
                .andExpect(jsonPath("$.state").value("FOLLOWING"));

        verify(socialGraphService).follow(principal.id(), targetId);
    }

    @Test
    void requiresCsrfForFollowMutation() throws Exception {
        mockMvc.perform(put("/v1/me/following/{targetUserId}", UUID.randomUUID())
                        .with(user(principal())))
                .andExpect(status().isForbidden());
    }

    private AuthenticatedUser principal() {
        return new AuthenticatedUser(
                UUID.randomUUID(), "maria@example.com", "maria", "Maria",
                "encoded-password", List.of(new SimpleGrantedAuthority("ROLE_USER")), true);
    }
}
