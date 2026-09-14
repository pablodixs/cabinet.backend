package com.scriptles.cabinet.user.dto.response;

import java.util.UUID;

public record ConsumptionReportItem(
        String key,
        String label,
        UUID personId,
        String imageUrl,
        long count
) {
}
