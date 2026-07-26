package com.scriptles.cabinet.media.controller;

import com.scriptles.cabinet.media.dto.response.MediaSearchPageResponse;
import com.scriptles.cabinet.media.enums.MediaSearchSort;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.service.MediaSearchService;
import com.scriptles.cabinet.media.translation.CatalogLocaleResolver;
import com.scriptles.cabinet.media.enums.SupportedLocale;
import com.scriptles.cabinet.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MediaSearchController.class)
@Import(SecurityConfig.class)
class MediaSearchControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MediaSearchService mediaSearchService;

    @MockitoBean
    private CatalogLocaleResolver catalogLocaleResolver;

    @Test
    void allowsAnonymousSearchWithDefaults() throws Exception {
        when(catalogLocaleResolver.resolve(null, null)).thenReturn(SupportedLocale.PT_BR);
        when(mediaSearchService.search("matrix", null, MediaSearchSort.RELEVANCE, null, 20, "pt-BR"))
                .thenReturn(new MediaSearchPageResponse(List.of(), null));

        mockMvc.perform(get("/v1/media/search").param("query", "matrix"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.nextCursor").doesNotExist());

        verify(mediaSearchService).search(
                "matrix", null, MediaSearchSort.RELEVANCE, null, 20, "pt-BR");
    }

    @Test
    void validatesQueryAndLimit() throws Exception {
        mockMvc.perform(get("/v1/media/search")
                        .param("query", "ab")
                        .param("limit", "41"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mapsTypeSortAndCursor() throws Exception {
        when(catalogLocaleResolver.resolve(null, null)).thenReturn(SupportedLocale.PT_BR);
        when(mediaSearchService.search(
                "matrix", MediaType.MOVIE, MediaSearchSort.RATING, "cursor", 10, "pt-BR"))
                .thenReturn(new MediaSearchPageResponse(List.of(), null));

        mockMvc.perform(get("/v1/media/search")
                        .param("query", "matrix")
                        .param("type", "MOVIE")
                        .param("sort", "RATING")
                        .param("cursor", "cursor")
                        .param("limit", "10"))
                .andExpect(status().isOk());

        verify(mediaSearchService).search(
                "matrix", MediaType.MOVIE, MediaSearchSort.RATING, "cursor", 10, "pt-BR");
    }
}
