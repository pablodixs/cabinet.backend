package com.scriptles.cabinet.media.dto.response;

import com.scriptles.cabinet.media.enums.AwardDatePrecision;
import com.scriptles.cabinet.media.enums.AwardOrigin;
import com.scriptles.cabinet.media.enums.AwardResult;
import com.scriptles.cabinet.media.enums.AwardSubjectType;

import java.time.LocalDate;
import java.util.UUID;

public record ModerationAwardResponse(
        UUID id,
        AwardSubjectType subjectType,
        UUID subjectId,
        AwardResult result,
        String programQid,
        String programName,
        String categoryQid,
        String categoryName,
        String ceremonyQid,
        String ceremonyName,
        LocalDate eventDate,
        Integer eventYear,
        AwardDatePrecision datePrecision,
        String workQid,
        String workName,
        UUID workMediaId,
        AwardOrigin origin,
        String sourceStatementId,
        String sourceUrl,
        boolean curated,
        boolean hidden,
        long version
) {
}
