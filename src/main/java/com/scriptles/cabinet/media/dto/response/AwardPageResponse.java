package com.scriptles.cabinet.media.dto.response;

import com.scriptles.cabinet.media.enums.AwardDatePrecision;
import com.scriptles.cabinet.media.enums.AwardOrigin;
import com.scriptles.cabinet.media.enums.AwardResult;
import com.scriptles.cabinet.media.enums.AwardSectionState;
import com.scriptles.cabinet.media.enums.AwardSubjectType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AwardPageResponse(
        UUID subjectId,
        AwardSubjectType subjectType,
        AwardSectionState state,
        Instant fetchedAt,
        Instant expiresAt,
        long totalWins,
        long totalNominations,
        List<Item> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public boolean pendingWithoutData() {
        return state == AwardSectionState.PENDING && totalElements == 0;
    }

    public record Item(
            UUID id,
            AwardResult result,
            Reference program,
            Reference category,
            Reference ceremony,
            LocalDate eventDate,
            Integer eventYear,
            AwardDatePrecision datePrecision,
            Work work,
            AwardOrigin origin,
            boolean curated,
            String sourceUrl
    ) {
    }

    public record Reference(String qid, String name) {
    }

    public record Work(UUID mediaId, String wikidataId, String title) {
    }
}
