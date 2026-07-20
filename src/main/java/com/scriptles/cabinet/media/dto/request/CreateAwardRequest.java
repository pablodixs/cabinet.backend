package com.scriptles.cabinet.media.dto.request;

import com.scriptles.cabinet.media.enums.AwardDatePrecision;
import com.scriptles.cabinet.media.enums.AwardResult;
import com.scriptles.cabinet.media.enums.AwardSubjectType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record CreateAwardRequest(
        @NotNull AwardSubjectType subjectType,
        @NotNull UUID subjectId,
        @NotNull AwardResult result,
        @Pattern(regexp = "^Q[1-9]\\d*$") String programQid,
        @Size(max = 300) String programName,
        @Pattern(regexp = "^Q[1-9]\\d*$") String categoryQid,
        @NotBlank @Size(max = 300) String categoryName,
        @Pattern(regexp = "^Q[1-9]\\d*$") String ceremonyQid,
        @Size(max = 300) String ceremonyName,
        LocalDate eventDate,
        @Min(1) @Max(9999) Integer eventYear,
        AwardDatePrecision datePrecision,
        @Pattern(regexp = "^Q[1-9]\\d*$") String workQid,
        @Size(max = 300) String workName,
        @Size(max = 2000) String sourceUrl
) {
}
