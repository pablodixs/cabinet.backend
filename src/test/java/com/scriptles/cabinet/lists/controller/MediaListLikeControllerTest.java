package com.scriptles.cabinet.lists.controller;

import com.scriptles.cabinet.lists.dto.response.MediaListLikeResponse;
import com.scriptles.cabinet.lists.service.MediaListLikeService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MediaListLikeController.class)
@Import(SecurityConfig.class)
class MediaListLikeControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MediaListLikeService mediaListLikeService;

    @Test
    void rejectsAnonymousListLikeLookup() throws Exception {
        mockMvc.perform(get("/v1/me/list-likes/{listId}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void likesListWithCsrf() throws Exception {
        AuthenticatedUser principal = principal();
        UUID listId = UUID.randomUUID();
        when(mediaListLikeService.like(principal.id(), listId))
                .thenReturn(new MediaListLikeResponse(true, 9));

        mockMvc.perform(put("/v1/me/list-likes/{listId}", listId)
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(true))
                .andExpect(jsonPath("$.likeCount").value(9));

        verify(mediaListLikeService).like(principal.id(), listId);
    }

    @Test
    void requiresCsrfForListLikeMutation() throws Exception {
        mockMvc.perform(put("/v1/me/list-likes/{listId}", UUID.randomUUID())
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
