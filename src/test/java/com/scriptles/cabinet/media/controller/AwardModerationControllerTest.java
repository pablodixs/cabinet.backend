package com.scriptles.cabinet.media.controller;

import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.media.enums.AwardSubjectType;
import com.scriptles.cabinet.media.service.AwardModerationService;
import com.scriptles.cabinet.security.AuthenticatedUser;
import com.scriptles.cabinet.security.CommunityAuthorization;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AwardModerationController.class)
@Import(SecurityConfig.class)
class AwardModerationControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AwardModerationService service;

    @MockitoBean(name = "communityAuthorization")
    private CommunityAuthorization communityAuthorization;

    @Test
    void rejectsAnonymousModerationLookup() throws Exception {
        mockMvc.perform(get("/v1/moderation/awards")
                        .param("subjectType", "MEDIA")
                        .param("subjectId", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void letsModeratorInspectAllEntriesIncludingHiddenOnes() throws Exception {
        AuthenticatedUser principal = principal();
        UUID mediaId = UUID.randomUUID();
        when(communityAuthorization.isModerator(any())).thenReturn(true);
        when(service.find(AwardSubjectType.MEDIA, mediaId, 0, 20))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/v1/moderation/awards")
                        .with(user(principal))
                        .param("subjectType", "MEDIA")
                        .param("subjectId", mediaId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());

        verify(service).find(AwardSubjectType.MEDIA, mediaId, 0, 20);
    }

    private AuthenticatedUser principal() {
        return new AuthenticatedUser(
                UUID.randomUUID(), "moderator@example.com", "moderator", "Moderador",
                "encoded-password", List.of(new SimpleGrantedAuthority("ROLE_MODERATOR")), true);
    }
}
