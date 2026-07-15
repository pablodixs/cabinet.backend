package com.scriptles.cabinet.security;

import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.user.controller.UserController;
import com.scriptles.cabinet.user.dto.response.ProfileActivityResponse;
import com.scriptles.cabinet.user.dto.response.UserProfileResponse;
import com.scriptles.cabinet.user.service.UserProfileService;
import com.scriptles.cabinet.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserProfileSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UserProfileService userProfileService;

    @Test
    void allowsAnonymousUsersToReadPublicProfiles() throws Exception {
        UserProfileResponse response = new UserProfileResponse(
                UUID.randomUUID(),
                "maria",
                "Maria Cabinet",
                "Livros, discos e filmes.",
                null,
                null,
                false,
                12,
                7,
                2,
                List.of()
        );
        when(userProfileService.findByUsername("maria", null)).thenReturn(response);

        mockMvc.perform(get("/v1/users/maria/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("maria"))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.libraryCount").value(12));
    }

    @Test
    void allowsAnonymousUsersToReadPublicProfileActivities() throws Exception {
        when(userProfileService.findActivities("maria", null, 0, 20))
                .thenReturn(new PageResponse<ProfileActivityResponse>(
                        List.of(),
                        0,
                        20,
                        0,
                        0
                ));

        mockMvc.perform(get("/v1/users/maria/activities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.page").value(0));
    }
}
