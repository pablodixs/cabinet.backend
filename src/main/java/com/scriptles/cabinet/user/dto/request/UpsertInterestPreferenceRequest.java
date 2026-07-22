package com.scriptles.cabinet.user.dto.request;

import com.scriptles.cabinet.user.enums.InterestPreference;
import com.scriptles.cabinet.user.enums.InterestTargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpsertInterestPreferenceRequest(
        @NotNull InterestTargetType targetType,
        @NotBlank @Size(max = 100) String targetId,
        @NotNull InterestPreference preference
) {
}
