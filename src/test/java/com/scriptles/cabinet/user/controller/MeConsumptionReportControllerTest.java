package com.scriptles.cabinet.user.controller;

import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.security.AuthenticatedUser;
import com.scriptles.cabinet.security.SecurityConfig;
import com.scriptles.cabinet.user.dto.response.ConsumptionReportResponse;
import com.scriptles.cabinet.user.dto.response.ConsumptionReportSection;
import com.scriptles.cabinet.user.enums.AccountTier;
import com.scriptles.cabinet.user.enums.ConsumptionReportPeriod;
import com.scriptles.cabinet.user.service.ConsumptionReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MeConsumptionReportController.class)
@Import(SecurityConfig.class)
class MeConsumptionReportControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean ConsumptionReportService service;

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/v1/me/consumption-report")
                        .param("year", "2026")
                        .param("month", "1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsFreeMembersWithoutCallingService() throws Exception {
        mockMvc.perform(get("/v1/me/consumption-report")
                        .with(user(principal(AccountTier.FREE)))
                        .param("year", "2026")
                        .param("month", "1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRO_REQUIRED"));
    }

    @Test
    void forwardsReportParametersForProMembers() throws Exception {
        AuthenticatedUser principal = principal(AccountTier.PRO);
        ConsumptionReportResponse response = new ConsumptionReportResponse(
                ConsumptionReportPeriod.MONTH, 2026, 1, MediaType.MOVIE, 2,
                List.of(), emptySection(), emptySection(), emptySection(),
                emptySection(), emptySection(), emptySection());
        when(service.find(principal.id(), ConsumptionReportPeriod.MONTH, 2026, 1, MediaType.MOVIE))
                .thenReturn(response);

        mockMvc.perform(get("/v1/me/consumption-report")
                        .with(user(principal))
                        .param("year", "2026")
                        .param("month", "1")
                        .param("mediaType", "MOVIE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalConsumptions").value(2))
                .andExpect(jsonPath("$.mediaType").value("MOVIE"));

        verify(service).find(principal.id(), ConsumptionReportPeriod.MONTH, 2026, 1, MediaType.MOVIE);
    }

    private ConsumptionReportSection emptySection() {
        return new ConsumptionReportSection(List.of(), 0, 0);
    }

    private AuthenticatedUser principal(AccountTier tier) {
        return new AuthenticatedUser(
                UUID.randomUUID(), "report@example.com", "reporter", "Reporter",
                "encoded-password", List.of(new SimpleGrantedAuthority("ROLE_USER")), true,
                com.scriptles.cabinet.user.enums.UserRole.USER, tier);
    }
}
