package com.scriptles.cabinet.user.dto.response;

import com.scriptles.cabinet.user.enums.ConsumptionReportPeriod;
import com.scriptles.cabinet.media.enums.MediaType;

import java.util.List;

public record ConsumptionReportResponse(
        ConsumptionReportPeriod period,
        int year,
        Integer month,
        MediaType mediaType,
        long totalConsumptions,
        List<ConsumptionReportPeriodOption> availablePeriods,
        ConsumptionReportSection topDirectors,
        ConsumptionReportSection topArtists,
        ConsumptionReportSection topGenres,
        ConsumptionReportSection topCountries,
        ConsumptionReportSection topLanguages,
        ConsumptionReportSection topReleaseYear
) {
}
