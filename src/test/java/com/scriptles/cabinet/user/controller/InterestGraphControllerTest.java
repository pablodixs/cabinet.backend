package com.scriptles.cabinet.user.controller;

import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.media.translation.CatalogLocaleResolver;
import com.scriptles.cabinet.security.AuthenticatedUser;
import com.scriptles.cabinet.security.SecurityConfig;
import com.scriptles.cabinet.user.dto.response.InterestResponse;
import com.scriptles.cabinet.user.dto.response.RecommendationResponse;
import com.scriptles.cabinet.user.enums.InterestPreference;
import com.scriptles.cabinet.user.enums.InterestTargetType;
import com.scriptles.cabinet.user.service.InterestGraphService;
import com.scriptles.cabinet.user.service.RecommendationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InterestGraphController.class)
@Import({SecurityConfig.class, CatalogLocaleResolver.class})
class InterestGraphControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean InterestGraphService interestGraphService;
    @MockitoBean RecommendationService recommendationService;

    @Test
    void protectsPrivateRecommendations() throws Exception {
        mockMvc.perform(get("/v1/me/recommendations"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void resolvesRecommendationLocaleFromHeader() throws Exception {
        AuthenticatedUser principal = principal();
        when(recommendationService.recommendations(principal.id(), null, 20, "en-US"))
                .thenReturn(new RecommendationResponse(List.of()));

        mockMvc.perform(get("/v1/me/recommendations")
                        .with(user(principal))
                        .header("Accept-Language", "en-US,en;q=0.9"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Content-Language", "en-US"))
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(
                        result.getResponse().getHeaders("Vary")).contains("Accept-Language"));

        verify(recommendationService).recommendations(principal.id(), null, 20, "en-US");
    }

    @Test
    void explicitRecommendationLocaleOverridesHeader() throws Exception {
        AuthenticatedUser principal = principal();
        when(recommendationService.recommendations(principal.id(), null, 20, "pt-BR"))
                .thenReturn(new RecommendationResponse(List.of()));

        mockMvc.perform(get("/v1/me/recommendations")
                        .with(user(principal))
                        .param("locale", "pt-BR")
                        .header("Accept-Language", "en-US"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Content-Language", "pt-BR"));

        verify(recommendationService).recommendations(principal.id(), null, 20, "pt-BR");
    }

    @Test
    void returnsInterestPageForAuthenticatedOwner() throws Exception {
        AuthenticatedUser principal = principal();
        InterestResponse interest = new InterestResponse(InterestTargetType.GENRE, "drama", "Drama",
                InterestPreference.POSITIVE, null, true, 0.6);
        when(interestGraphService.interests(
                principal.id(), InterestTargetType.GENRE, 0, 20, "pt-BR"))
                .thenReturn(new PageResponse<>(List.of(interest), 0, 20, 1, 1));

        mockMvc.perform(get("/v1/me/interests")
                        .param("targetType", "GENRE").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].targetId").value("drama"))
                .andExpect(jsonPath("$.items[0].strength").value(0.6));
    }

    @Test
    void validatesAndUpsertsExplicitPreferenceWithCsrf() throws Exception {
        AuthenticatedUser principal = principal();
        InterestResponse response = new InterestResponse(InterestTargetType.GENRE, "drama", "Drama",
                InterestPreference.NEGATIVE, InterestPreference.NEGATIVE, true, 1.0);
        when(interestGraphService.upsert(eq(principal.id()), any())).thenReturn(response);

        mockMvc.perform(put("/v1/me/interests").with(user(principal)).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"targetType":"GENRE","targetId":"drama","preference":"NEGATIVE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.explicitPreference").value("NEGATIVE"));

        verify(interestGraphService).upsert(eq(principal.id()), any());
    }

    @Test
    void rejectsInvalidPreferenceBody() throws Exception {
        mockMvc.perform(put("/v1/me/interests").with(user(principal())).with(csrf())
                        .contentType("application/json")
                        .content("{\"targetType\":\"GENRE\",\"targetId\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private AuthenticatedUser principal() {
        return new AuthenticatedUser(
                UUID.randomUUID(), "maria@example.com", "maria", "Maria", "encoded-password",
                List.of(new SimpleGrantedAuthority("ROLE_USER")), true);
    }
}
