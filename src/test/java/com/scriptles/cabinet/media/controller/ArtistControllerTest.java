package com.scriptles.cabinet.media.controller;

import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.media.dto.response.ArtistResponse;
import com.scriptles.cabinet.media.dto.response.ArtistWorkResponse;
import com.scriptles.cabinet.media.enums.CreditRole;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.service.ArtistService;
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

@WebMvcTest(ArtistController.class)
@Import(SecurityConfig.class)
class ArtistControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ArtistService artistService;

    @Test
    void returnsPublicArtistDetails() throws Exception {
        UUID artistId = UUID.randomUUID();
        when(artistService.findDetails(artistId)).thenReturn(new ArtistResponse(
                artistId,
                "David Fincher",
                "Cineasta norte-americano.",
                "https://image.tmdb.org/t/p/w500/profile.jpg",
                ExternalSource.TMDB,
                "7467",
                3,
                List.of(CreditRole.DIRECTOR, CreditRole.PRODUCER)
        ));

        mockMvc.perform(get("/v1/artists/{artistId}", artistId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(artistId.toString()))
                .andExpect(jsonPath("$.name").value("David Fincher"))
                .andExpect(jsonPath("$.workCount").value(3))
                .andExpect(jsonPath("$.roles[0]").value("DIRECTOR"));
    }

    @Test
    void returnsPublicPaginatedWorksWithDefaults() throws Exception {
        UUID artistId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        ArtistWorkResponse work = new ArtistWorkResponse(
                mediaId,
                MediaType.MOVIE,
                "Fight Club",
                null,
                LocalDate.of(1999, 10, 15),
                List.of(new ArtistWorkResponse.CreditResponse(CreditRole.DIRECTOR, null))
        );
        when(artistService.findWorks(artistId, 0, 24))
                .thenReturn(new PageResponse<>(List.of(work), 0, 24, 1, 1));

        mockMvc.perform(get("/v1/artists/{artistId}/works", artistId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].mediaId").value(mediaId.toString()))
                .andExpect(jsonPath("$.items[0].credits[0].role").value("DIRECTOR"))
                .andExpect(jsonPath("$.totalElements").value(1));

        verify(artistService).findWorks(artistId, 0, 24);
    }

    @Test
    void validatesPagination() throws Exception {
        mockMvc.perform(get("/v1/artists/{artistId}/works", UUID.randomUUID())
                        .param("page", "-1")
                        .param("size", "41"))
                .andExpect(status().isBadRequest());
    }
}
