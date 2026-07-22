package com.scriptles.cabinet.user.dto.response;

import com.scriptles.cabinet.user.enums.InterestPreference;
import com.scriptles.cabinet.user.enums.InterestTargetType;

public record InterestResponse(
        InterestTargetType targetType,
        String targetId,
        String label,
        InterestPreference polarity,
        InterestPreference explicitPreference,
        boolean inferred,
        double strength
) {
}
