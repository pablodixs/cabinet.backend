package com.scriptles.cabinet.security;

import com.scriptles.cabinet.profile.controller.HQConsoleController;
import com.scriptles.cabinet.profile.enums.HQMemberRole;
import com.scriptles.cabinet.profile.service.HQOperatorService;
import com.scriptles.cabinet.profile.service.PostService;
import com.scriptles.cabinet.profile.service.ProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HQConsoleController.class)
@Import(SecurityConfig.class)
class HQConsoleSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean HQOperatorService operators;
    @MockitoBean ProfileService profiles;
    @MockitoBean PostService posts;
    @MockitoBean com.scriptles.cabinet.user.service.SupabaseAvatarStorage storage;

    @Test void personalAccountCannotEnterHQConsole() throws Exception {
        mvc.perform(get("/v1/hq-console/me").with(user("member").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test void hqAccountCannotEnterPersonalArea() throws Exception {
        AuthenticatedHQ hq = principal();
        mvc.perform(get("/v1/auth/me").with(user(hq)))
                .andExpect(status().isForbidden());
    }

    @Test void hqAccountCanEnterItsConsole() throws Exception {
        AuthenticatedHQ hq = principal();
        when(operators.refresh(hq.operatorId())).thenReturn(hq);
        mvc.perform(get("/v1/hq-console/me").with(user(hq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileId").value(hq.profileId().toString()));
    }

    @Test void hqLoginCreatesAnIndependentSession() throws Exception {
        AuthenticatedHQ hq = principal();
        when(operators.authenticate("studio", hq.email(), "long-password")).thenReturn(hq);
        when(operators.refresh(hq.operatorId())).thenReturn(hq);
        var login = mvc.perform(post("/v1/hq-console/login").with(csrf())
                        .contentType("application/json")
                        .content("{\"handle\":\"studio\",\"email\":\"editor@example.com\",\"password\":\"long-password\"}"))
                .andExpect(status().isOk())
                .andReturn();
        mvc.perform(get("/v1/hq-console/me").session((org.springframework.mock.web.MockHttpSession) login.getRequest().getSession(false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(hq.email()));
    }

    private AuthenticatedHQ principal() {
        return new AuthenticatedHQ(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "editor@example.com", "Editor", HQMemberRole.EDITOR, true);
    }
}
