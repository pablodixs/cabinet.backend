package com.scriptles.cabinet.lists.controller;

import com.scriptles.cabinet.lists.dto.response.PublicMediaListDetailsResponse;
import com.scriptles.cabinet.lists.dto.response.PublicMediaListResponse;
import com.scriptles.cabinet.lists.service.MediaListService;
import com.scriptles.cabinet.security.SecurityConfig;
import com.scriptles.cabinet.user.enums.Visibility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PublicListController.class)
@Import(SecurityConfig.class)
class PublicListControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MediaListService mediaListService;

    @Test
    void returnsPublicListDetailsWithoutAuthentication() throws Exception {
        UUID listId = UUID.randomUUID();
        PublicMediaListDetailsResponse response = new PublicMediaListDetailsResponse(
                listId,
                "Cinema de estrada",
                "Filmes atravessados por paisagens.",
                Visibility.PUBLIC,
                true,
                null,
                12,
                8,
                false,
                false,
                Instant.parse("2026-07-15T12:00:00Z"),
                Instant.parse("2026-07-15T12:00:00Z"),
                new PublicMediaListResponse.AuthorResponse(
                        UUID.randomUUID(),
                        "maria",
                        "Maria",
                        null
                ),
                List.of()
        );
        when(mediaListService.findAccessibleDetails(null, listId)).thenReturn(response);

        mockMvc.perform(get("/v1/lists/{listId}", listId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cinema de estrada"))
                .andExpect(jsonPath("$.likeCount").value(8))
                .andExpect(jsonPath("$.ownList").value(false))
                .andExpect(jsonPath("$.owner.username").value("maria"));

        verify(mediaListService).findAccessibleDetails(null, listId);
    }
}
