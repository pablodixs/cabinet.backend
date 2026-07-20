package com.scriptles.cabinet.user.controller;

import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.security.AuthenticatedUser;
import com.scriptles.cabinet.security.SecurityConfig;
import com.scriptles.cabinet.user.dto.request.CreateDiaryEntryRequest;
import com.scriptles.cabinet.user.service.DiaryService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({MeDiaryController.class, PublicDiaryController.class})
@Import(SecurityConfig.class)
class DiaryControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean DiaryService diaryService;

    @Test
    void personalDiaryRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/v1/me/diary"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publicDiaryCanBeReadAnonymously() throws Exception {
        when(diaryService.findByUsername("maria", null, 0, 20))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/v1/users/maria/diary"))
                .andExpect(status().isOk());
    }

    @Test
    void authenticatedMemberCanCreateAnEntryWithCsrf() throws Exception {
        AuthenticatedUser principal = principal();
        UUID mediaId = UUID.randomUUID();
        mockMvc.perform(post("/v1/me/diary")
                        .with(user(principal))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "mediaId": "%s",
                                  "occurredOn": "2026-07-19",
                                  "reconsumption": false,
                                  "visibility": "PUBLIC",
                                  "tags": []
                                }
                                """.formatted(mediaId)))
                .andExpect(status().isCreated());

        verify(diaryService).create(eq(principal.id()), any(CreateDiaryEntryRequest.class));
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
