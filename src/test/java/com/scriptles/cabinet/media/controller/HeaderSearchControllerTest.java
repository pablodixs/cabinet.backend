package com.scriptles.cabinet.media.controller;

import com.scriptles.cabinet.media.dto.response.HeaderSearchResponse;
import com.scriptles.cabinet.media.enums.HeaderSearchScope;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.service.HeaderSearchService;
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

@WebMvcTest(HeaderSearchController.class)
@Import(SecurityConfig.class)
class HeaderSearchControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HeaderSearchService headerSearchService;

    @Test
    void allowsAnonymousHeaderSearchWithDefaults() throws Exception {
        when(headerSearchService.search("matrix", HeaderSearchScope.ALL, null))
                .thenReturn(new HeaderSearchResponse(List.of()));

        mockMvc.perform(get("/v1/search/header").param("query", "matrix"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());

        verify(headerSearchService).search("matrix", HeaderSearchScope.ALL, null);
    }

    @Test
    void mapsScopeAndMediaTypeFilters() throws Exception {
        when(headerSearchService.search("matrix", HeaderSearchScope.MEDIA, MediaType.MOVIE))
                .thenReturn(new HeaderSearchResponse(List.of()));

        mockMvc.perform(get("/v1/search/header")
                        .param("query", "matrix")
                        .param("scope", "MEDIA")
                        .param("type", "MOVIE"))
                .andExpect(status().isOk());

        verify(headerSearchService).search("matrix", HeaderSearchScope.MEDIA, MediaType.MOVIE);
    }

    @Test
    void rejectsQueriesOutsideHeaderBounds() throws Exception {
        mockMvc.perform(get("/v1/search/header").param("query", "a"))
                .andExpect(status().isBadRequest());
    }
}
