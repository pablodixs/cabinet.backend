package com.scriptles.cabinet.user.dto.response;

import com.scriptles.cabinet.user.enums.InterestTargetType;

public record InterestOptionResponse(
        InterestTargetType targetType,
        String targetId,
        String label,
        String subtitle,
        String imageUrl
) {
}
