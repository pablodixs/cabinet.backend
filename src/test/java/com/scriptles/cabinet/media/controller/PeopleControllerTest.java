package com.scriptles.cabinet.media.controller;

import com.scriptles.cabinet.media.dto.response.AwardPageResponse;
import com.scriptles.cabinet.media.enums.AwardSectionState;
import com.scriptles.cabinet.media.enums.AwardSubjectType;
import com.scriptles.cabinet.media.service.ArtistService;
import com.scriptles.cabinet.media.service.AwardQueryService;
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
@Import(SecurityConfig.class)
class PeopleControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ArtistService artistService;

    @MockitoBean
    private AwardQueryService awardQueryService;

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
}
