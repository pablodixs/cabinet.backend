package com.scriptles.cabinet.media.controller;

import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.media.dto.response.MediaSearchItemResponse;
import com.scriptles.cabinet.media.dto.response.TrendingMediaResponse;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.service.MediaRankingService;
import com.scriptles.cabinet.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MediaRankingController.class)
@Import(SecurityConfig.class)
class MediaRankingControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MediaRankingService mediaRankingService;

    @Test
    void returnsPublicTopRatedRanking() throws Exception {
        MediaSearchItemResponse item = item("Central do Brasil", 4.8, 32);
        when(mediaRankingService.topRated(MediaType.MOVIE, 0, 20))
                .thenReturn(new PageResponse<>(List.of(item), 0, 20, 1, 1));

        mockMvc.perform(get("/v1/media/rankings/top-rated").param("type", "MOVIE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].title").value("Central do Brasil"))
                .andExpect(jsonPath("$.items[0].averageRating").value(4.8))
                .andExpect(jsonPath("$.items[0].ratingCount").value(32))
                .andExpect(jsonPath("$.totalElements").value(1));

        verify(mediaRankingService).topRated(MediaType.MOVIE, 0, 20);
    }

    @Test
    void returnsTrendingMediaWithDefaultWindow() throws Exception {
        MediaSearchItemResponse item = item("Ainda Estou Aqui", 4.7, 21);
        when(mediaRankingService.trending(null, 7, 12))
                .thenReturn(new TrendingMediaResponse(List.of(item), 7));

        mockMvc.perform(get("/v1/media/rankings/trending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodDays").value(7))
                .andExpect(jsonPath("$.items[0].title").value("Ainda Estou Aqui"));

        verify(mediaRankingService).trending(null, 7, 12);
    }

    @Test
    void validatesRankingParameters() throws Exception {
        mockMvc.perform(get("/v1/media/rankings/top-rated").param("page", "-1"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/v1/media/rankings/trending").param("days", "31"))
                .andExpect(status().isBadRequest());
    }

    private MediaSearchItemResponse item(String title, double averageRating, long ratingCount) {
        return new MediaSearchItemResponse(
                UUID.randomUUID(), "123", ExternalSource.TMDB, MediaType.MOVIE, title,
                "Walter Salles", null, "https://example.com/cover.jpg",
                LocalDate.of(2024, 11, 7), true, averageRating, ratingCount
        );
    }
}
