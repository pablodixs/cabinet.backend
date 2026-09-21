package com.scriptles.cabinet.media.controller;

import com.scriptles.cabinet.common.api.PageResponse;
import com.scriptles.cabinet.media.dto.response.AwardPageResponse;
import com.scriptles.cabinet.media.dto.response.PersonWorkResponse;
import com.scriptles.cabinet.media.enums.AwardSectionState;
import com.scriptles.cabinet.media.enums.AwardSubjectType;
import com.scriptles.cabinet.media.enums.CreditRole;
import com.scriptles.cabinet.media.enums.ExternalSource;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.media.service.ArtistService;
import com.scriptles.cabinet.media.service.AwardQueryService;
import com.scriptles.cabinet.media.service.PersonWorksService;
import com.scriptles.cabinet.media.translation.CatalogLocaleResolver;
import com.scriptles.cabinet.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PeopleController.class)
@Import({SecurityConfig.class, CatalogLocaleResolver.class})
class PeopleControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ArtistService artistService;

    @MockitoBean
    private AwardQueryService awardQueryService;

    @MockitoBean
    private PersonWorksService personWorksService;

    @Test
    void returnsUnifiedPersonWorksWithFiltersAndPagination() throws Exception {
        UUID personId = UUID.randomUUID();
        PersonWorkResponse work = new PersonWorkResponse(
                null, "550", ExternalSource.TMDB, MediaType.MOVIE, "Fight Club", null,
                null, false, List.of(new PersonWorkResponse.CreditResponse(CreditRole.ACTOR, "Tyler Durden"))
        );
        when(personWorksService.findWorks(personId, 1, 12, "en-US", MediaType.MOVIE))
                .thenReturn(new PageResponse<>(List.of(work), 1, 12, 13, 2));

        mockMvc.perform(get("/v1/people/{personId}/works", personId)
                        .param("page", "1")
                        .param("size", "12")
                        .param("language", "en-US")
                        .header("Accept-Language", "pt-BR")
                        .param("type", "MOVIE"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Content-Language", "en-US"))
                .andExpect(jsonPath("$.items[0].externalId").value("550"))
                .andExpect(jsonPath("$.items[0].credits[0].role").value("ACTOR"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(12))
                .andExpect(jsonPath("$.totalElements").value(13));

        verify(personWorksService).findWorks(personId, 1, 12, "en-US", MediaType.MOVIE);
    }

    @Test
    void returnsAcceptedWhilePersonAwardsAreLoading() throws Exception {
        UUID personId = UUID.randomUUID();
        AwardPageResponse response = new AwardPageResponse(
                personId, AwardSubjectType.PERSON, AwardSectionState.PENDING,
                null, null, 0, 0, List.of(), 0, 20, 0, 0);
        when(awardQueryService.findPerson(personId, null, 0, 20)).thenReturn(response);

        mockMvc.perform(get("/v1/people/{personId}/awards", personId))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Retry-After", "2"))
                .andExpect(jsonPath("$.subjectId").value(personId.toString()))
                .andExpect(jsonPath("$.subjectType").value("PERSON"))
                .andExpect(jsonPath("$.state").value("PENDING"));

        verify(awardQueryService).findPerson(personId, null, 0, 20);
    }

    @Test
    void validatesAwardPagination() throws Exception {
        mockMvc.perform(get("/v1/people/{personId}/awards", UUID.randomUUID())
                        .param("page", "-1")
                        .param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validatesWorksPagination() throws Exception {
        mockMvc.perform(get("/v1/people/{personId}/works", UUID.randomUUID())
                        .param("page", "-1")
                        .param("size", "41"))
                .andExpect(status().isBadRequest());
    }
}
