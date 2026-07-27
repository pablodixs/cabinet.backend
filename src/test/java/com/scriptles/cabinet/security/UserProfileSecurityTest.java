package com.scriptles.cabinet.security;

import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.user.controller.UserController;
import com.scriptles.cabinet.user.dto.response.ProfileActivityResponse;
import com.scriptles.cabinet.user.dto.response.ProfileStatsResponse;
import com.scriptles.cabinet.user.dto.response.UserSearchResponse;
import com.scriptles.cabinet.user.dto.response.UserProfileResponse;
import com.scriptles.cabinet.user.service.UserProfileService;
import com.scriptles.cabinet.user.service.UserService;
import com.scriptles.cabinet.user.service.SocialGraphService;
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

    @MockitoBean
    private SocialGraphService socialGraphService;

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
                new ProfileStatsResponse(320, 840, 5, 2, 1, 1, 3),
                List.of()
        );
        when(userProfileService.findByUsername("maria", null)).thenReturn(response);

        mockMvc.perform(get("/v1/users/maria/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("maria"))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.pro").value(false))
                .andExpect(jsonPath("$.libraryCount").value(12))
                .andExpect(jsonPath("$.stats.watchedMinutes").value(320))
                .andExpect(jsonPath("$.stats.pagesRead").value(840))
                .andExpect(jsonPath("$.stats.episodesWatched").value(5))
                .andExpect(jsonPath("$.stats.albumsConsumed").value(2))
                .andExpect(jsonPath("$.stats.moviesConsumed").value(1))
                .andExpect(jsonPath("$.stats.seriesConsumed").value(1))
                .andExpect(jsonPath("$.stats.booksConsumed").value(3));
    }

    @Test
    void allowsAnonymousUsersToSearchPublicProfiles() throws Exception {
        UserSearchResponse response = new UserSearchResponse(
                UUID.randomUUID(),
                "maria",
                "Maria Cabinet",
                "Livros, discos e filmes.",
                null
        );
        when(userProfileService.search("maria", 0, 20)).thenReturn(
                new PageResponse<>(List.of(response), 0, 20, 1, 1)
        );

        mockMvc.perform(get("/v1/users/search").param("query", "maria"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].username").value("maria"))
                .andExpect(jsonPath("$.items[0].displayName").value("Maria Cabinet"));
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

    @Test
    void allowsAnonymousUsersToReadPublicProfileActivitiesForMedia() throws Exception {
        UUID mediaId = UUID.randomUUID();
        when(userProfileService.findActivitiesByMedia("maria", mediaId, null))
                .thenReturn(List.of());

        mockMvc.perform(get("/v1/users/maria/activities/{mediaId}", mediaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }
}
