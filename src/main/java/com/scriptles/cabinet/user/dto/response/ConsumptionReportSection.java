package com.scriptles.cabinet.user.dto.response;

import java.util.List;

public record ConsumptionReportSection(
        List<ConsumptionReportItem> items,
        long eligibleCount,
        long attributedCount
) {
}
