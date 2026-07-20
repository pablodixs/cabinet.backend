package com.scriptles.cabinet.media.validation;

import com.scriptles.cabinet.common.api.ApiException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class RatingValue {
    private static final BigDecimal MIN = new BigDecimal("0.5");
    private static final BigDecimal MAX = new BigDecimal("5.0");
    private static final BigDecimal STEP = new BigDecimal("0.5");

    private RatingValue() {
    }

    public static BigDecimal normalize(BigDecimal value) {
        if (value == null || value.compareTo(MIN) < 0 || value.compareTo(MAX) > 0
                || value.remainder(STEP).compareTo(BigDecimal.ZERO) != 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RATING",
                    "A nota deve estar entre 0,5 e 5, em intervalos de meia estrela");
        }
        return value.setScale(1, RoundingMode.UNNECESSARY);
    }
}
