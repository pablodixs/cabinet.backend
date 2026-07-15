package com.scriptles.cabinet.lists.controller;

import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.lists.dto.response.PublicMediaListResponse;
import com.scriptles.cabinet.lists.service.MediaListService;
import com.scriptles.cabinet.security.SecurityConfig;
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

@WebMvcTest(PublicMediaListController.class)
@Import(SecurityConfig.class)
class PublicMediaListControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MediaListService mediaListService;

    @Test
    void returnsPublicListsWithoutAuthentication() throws Exception {
        UUID mediaId = UUID.randomUUID();
        UUID listId = UUID.randomUUID();
        PublicMediaListResponse response = new PublicMediaListResponse(
                listId,
                "Cinema de estrada",
                "Filmes que atravessam paisagens e afetos.",
                true,
                null,
                12,
                8,
                4,
                Instant.parse("2026-07-15T12:00:00Z"),
                new PublicMediaListResponse.AuthorResponse(
                        UUID.randomUUID(),
                        "maria",
                        "Maria",
                        null
                )
        );
        when(mediaListService.findPublicByMedia(mediaId, 0, 20))
                .thenReturn(new PageResponse<>(List.of(response), 0, 20, 1, 1));

        mockMvc.perform(get("/v1/media/{mediaId}/lists", mediaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(listId.toString()))
                .andExpect(jsonPath("$.items[0].name").value("Cinema de estrada"))
                .andExpect(jsonPath("$.items[0].mediaPosition").value(4))
                .andExpect(jsonPath("$.items[0].owner.username").value("maria"))
                .andExpect(jsonPath("$.totalElements").value(1));

        verify(mediaListService).findPublicByMedia(mediaId, 0, 20);
    }
}
