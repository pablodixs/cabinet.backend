package com.scriptles.cabinet.lists.controller;

import com.scriptles.cabinet.lists.dto.request.AddMediaListItemRequest;
import com.scriptles.cabinet.lists.dto.request.CreateMediaListRequest;
import com.scriptles.cabinet.lists.dto.response.MediaListItemResponse;
import com.scriptles.cabinet.lists.dto.response.MediaListResponse;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.lists.service.MediaListService;
import com.scriptles.cabinet.security.AuthenticatedUser;
import com.scriptles.cabinet.security.SecurityConfig;
import com.scriptles.cabinet.user.enums.Visibility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MediaListController.class)
@Import(SecurityConfig.class)
class MediaListControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MediaListService mediaListService;

    @Test
    void rejectsAnonymousListRequests() throws Exception {
        mockMvc.perform(get("/v1/me/lists"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void requiresCsrfForListCreation() throws Exception {
        mockMvc.perform(post("/v1/me/lists")
                        .with(user(principal()))
                        .contentType("application/json")
                        .content(validBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void createsListForAuthenticatedUser() throws Exception {
        AuthenticatedUser principal = principal();
        UUID listId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-15T12:00:00Z");
        MediaListResponse response = new MediaListResponse(
                listId,
                "Ficções favoritas",
                "Para reler com calma.",
                Visibility.PRIVATE,
                true,
                null,
                List.of(),
                0,
                now,
                now
        );
        when(mediaListService.create(
                eq(principal.id()),
                any(CreateMediaListRequest.class)
        )).thenReturn(response);

        mockMvc.perform(post("/v1/me/lists")
                        .with(user(principal))
                        .with(csrf())
                        .contentType("application/json")
                        .content(validBody()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/v1/me/lists/" + listId))
                .andExpect(jsonPath("$.name").value("Ficções favoritas"))
                .andExpect(jsonPath("$.visibility").value("PRIVATE"))
                .andExpect(jsonPath("$.itemCount").value(0));

        verify(mediaListService).create(
                eq(principal.id()),
                any(CreateMediaListRequest.class)
        );
    }

    @Test
    void returnsFieldErrorForBlankName() throws Exception {
        mockMvc.perform(post("/v1/me/lists")
                        .with(user(principal()))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": "   ",
                                  "visibility": "PUBLIC"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.name")
                        .value("Informe um nome para a lista"));
    }

    @Test
    void addsMediaToAnOwnedList() throws Exception {
        AuthenticatedUser principal = principal();
        UUID listId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        MediaListItemResponse response = new MediaListItemResponse(
                itemId,
                mediaId,
                1,
                null,
                MediaType.MOVIE,
                "Paris, Texas",
                null,
                null,
                null,
                null,
                Instant.parse("2026-07-15T12:00:00Z")
        );
        when(mediaListService.addItem(
                eq(principal.id()),
                eq(listId),
                any(AddMediaListItemRequest.class)
        )).thenReturn(response);

        mockMvc.perform(post("/v1/me/lists/{listId}/items", listId)
                        .with(user(principal))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "mediaId": "%s"
                                }
                                """.formatted(mediaId)))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location",
                        "/v1/me/lists/" + listId + "/items/" + itemId
                ))
                .andExpect(jsonPath("$.mediaId").value(mediaId.toString()))
                .andExpect(jsonPath("$.title").value("Paris, Texas"));
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

    private String validBody() {
        return """
                {
                  "name": "Ficções favoritas",
                  "description": "Para reler com calma.",
                  "visibility": "PRIVATE",
                  "ordered": true
                }
                """;
    }
}
