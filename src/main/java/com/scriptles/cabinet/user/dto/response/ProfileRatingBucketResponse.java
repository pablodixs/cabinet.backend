package com.scriptles.cabinet.user.dto.response;

import java.math.BigDecimal;

public record ProfileRatingBucketResponse(
        BigDecimal rating,
        long count
) {
}
