package com.scriptles.cabinet.user.controller;

import com.scriptles.cabinet.common.api.ApiException;
import com.scriptles.cabinet.security.AuthenticatedUser;
import com.scriptles.cabinet.media.enums.MediaType;
import com.scriptles.cabinet.user.dto.response.ConsumptionReportResponse;
import com.scriptles.cabinet.user.enums.ConsumptionReportPeriod;
import com.scriptles.cabinet.user.service.ConsumptionReportService;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/me/consumption-report")
@RequiredArgsConstructor
@Validated
public class MeConsumptionReportController {
    private final ConsumptionReportService service;

    @GetMapping
    public ConsumptionReportResponse find(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "MONTH") ConsumptionReportPeriod period,
            @RequestParam @Min(1) int year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) MediaType mediaType
    ) {
        if (user == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                    "É necessário iniciar sessão");
        }
        if (!user.pro()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PRO_REQUIRED",
                    "O relatório de consumo está disponível apenas para usuários PRO");
        }
        return service.find(user.id(), period, year, month, mediaType);
    }
}
